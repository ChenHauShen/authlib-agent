package yushijinhun.authlibagent.web.yggdrasil.servlet;

import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;
import yushijinhun.authlibagent.service.YggdrasilService;
import yushijinhun.authlibagent.web.yggdrasil.ResponseSerializer;

abstract public class YggdrasilServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	protected final Logger LOGGER = LogManager.getFormatterLogger(getClass());

	@Autowired
	protected YggdrasilService backend;

	@Autowired
	protected ResponseSerializer serializer;

	@Value("#{errorNames}")
	private Map<String, String> errorNames;

	@Value("#{errorCodes}")
	private Map<String, Integer> errorCodes;

	@Value("#{config['security.showErrorCause']}")
	private boolean showErrorCause;

	@Override
	public void init() throws ServletException {
		SpringBeanAutowiringSupport.processInjectionBasedOnServletContext(this, getServletContext());
	}

	protected void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		int respCode;
		Object jsonResp;
		try {
			jsonResp = process(req);
			if (jsonResp == null) {
				respCode = 204;
			} else {
				respCode = 200;
			}
		} catch (IOException | ServletException e) {
			throw e;
		} catch (Exception e) {
			log("exception during process request", e);

			respCode = getConfiguredErrorCode(e.getClass());
			if (respCode == -1) {
				respCode = 500;
				LOGGER.warn("unexcept exception", e);
			}

			String errorName = lookupErrorName(e);
			String message = e.getMessage();
			String cause = showErrorCause ? lookupErrorName(e.getCause()) : null;

			JSONObject errJson = new JSONObject();
			errJson.put("error", errorName);
			if (message != null)
				errJson.put("errorMessage", message);
			if (cause != null)
				errJson.put("cause", cause);
			jsonResp = errJson;
		}

		resp.setStatus(respCode);
		if (jsonResp != null) {
			resp.setContentType("application/json; charset=utf-8");
			resp.getWriter().print(jsonResp);
		}
	}

	abstract protected Object process(HttpServletRequest req) throws Exception;

	private String lookupErrorName(Throwable e) {
		if (e == null) {
			return null;
		}
		String exName = getConfiguredErrorName(e.getClass());
		if (exName == null) {
			exName = e.getClass().getSimpleName();
		}
		return exName;
	}

	private String getConfiguredErrorName(Class<?> clazz) {
		for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
			String exName = errorNames.get(currentClass.getCanonicalName());
			if (exName != null) {
				return exName;
			}
		}
		return null;
	}

	private int getConfiguredErrorCode(Class<?> clazz) {
		for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
			Integer code = errorCodes.get(currentClass.getCanonicalName());
			if (code != null) {
				return code;
			}
		}
		return -1;
	}

}
