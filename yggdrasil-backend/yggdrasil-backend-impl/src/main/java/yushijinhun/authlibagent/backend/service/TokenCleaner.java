package yushijinhun.authlibagent.backend.service;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yushijinhun.authlibagent.backend.model.ServerId;
import yushijinhun.authlibagent.backend.model.Token;
import static org.hibernate.criterion.Restrictions.lt;

@Component
public class TokenCleaner {

	private static final Logger LOGGER = LogManager.getFormatterLogger();

	private class CleanTask extends TimerTask {

		Class<?> entity;
		String timeField;
		long expireTime;

		CleanTask(Class<?> entity, String timeField, long expireTime) {
			this.entity = entity;
			this.timeField = timeField;
			this.expireTime = expireTime;
		}

		@Override
		public void run() {
			long earliestTime = System.currentTimeMillis() - expireTime;
			LOGGER.info("executing time expire clean up on %s. earliestTime=%d", entity.getSimpleName(), earliestTime);
			int cleans;

			// TODO: maybe we can use spring managed transaction here?
			Session session = sessionFactory.openSession();
			try {
				List<?> matches = session.createCriteria(entity).add(lt(timeField, earliestTime)).list();
				cleans = matches.size();
				matches.forEach(session::delete);
				session.flush();
			} catch (RuntimeException e) {
				// roll back for unchecked exceptions
				session.getTransaction().rollback();
				throw e;
			} finally {
				session.close();
			}

			LOGGER.info("executed time expire clean up on %s. clean up %d objects", entity.getSimpleName(), cleans);
		}

	}

	@Autowired
	private SessionFactory sessionFactory;

	@Value("#{config['expire.token.time']}")
	private long tokenExpireTime;

	@Value("#{config['expire.serverid.time']}")
	private long serveridExpireTime;

	@Value("#{config['expire.token.scantime']}")
	private long tokenScanTime;

	@Value("#{config['expire.serverid.scantime']}")
	private long serveridScanTime;

	private Timer timer;

	@PostConstruct
	private void startThread() {
		timer = new Timer("token-cleaner", true);

		// token
		timer.scheduleAtFixedRate(new CleanTask(Token.class, "createTime", tokenExpireTime), tokenScanTime, tokenScanTime);

		// server id
		timer.scheduleAtFixedRate(new CleanTask(ServerId.class, "createTime", serveridExpireTime), serveridScanTime, serveridScanTime);
	}

	@PreDestroy
	private void stopThread() {
	}

}