package br.com.http.queue;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response.Status;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class HttpRequestMessage implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(HttpRequestMessage.class);

	private final String method;
	private final String url;

	private String cookieName;
	private String cookieContent;
	private boolean useCookie = false;

	private String username;
	private String password;
	private boolean useBasicAuth = false;

	public HttpRequestMessage(String method, String url) {
		super();
		this.method = method;
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public void withCookie(String cookieName, String cookieContent) {
		this.useCookie = true;
		this.cookieName = cookieName;
		this.cookieContent = cookieContent;
	}

	public void withBasicAuth(String username, String password) {
		this.useBasicAuth = true;
		this.username = username;
		this.password = password;
	}

	public void send() {
		HttpClientBuilder httpClientBuilder = HttpClients.custom();

		if (useBasicAuth) {
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(new AuthScope("localhost", 443), new UsernamePasswordCredentials(username,
					password));
			httpClientBuilder = httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
		}

		try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
			HttpRequestBase http = null;

			if ("GET".equalsIgnoreCase(method)) {
				http = new HttpGet(url);
			} else if ("POST".equalsIgnoreCase(method)) {
				http = new HttpPost(url);
			} else if ("PUT".equalsIgnoreCase(method)) {
				http = new HttpPut(url);
			} else if ("DELETE".equalsIgnoreCase(method)) {
				http = new HttpDelete(url);
			} else {
				throw new RuntimeException("Unsupported HTTP method exception : " + method);
			}

			if (useCookie) {
				http.setHeader("Cookie", cookieName + "=" + cookieContent);
			}

			Stopwatch stopwatch = Stopwatch.createStarted();

			try (CloseableHttpResponse response = httpClient.execute(http)) {
				int responseStatus = response.getStatusLine().getStatusCode();
				String responseContent = EntityUtils.toString(response.getEntity());
				stopwatch.stop();

				if (responseStatus == Status.OK.getStatusCode()) {
					logger.info("Request to {} processed successfully with code {} [{} ms] : \n{}", url,
							responseStatus, stopwatch.elapsed(TimeUnit.MILLISECONDS), responseContent);
				} else {
					logger.info("Request to {} processed with error code {} [{} ms] : \n{}", url, responseStatus,
							stopwatch.elapsed(TimeUnit.MILLISECONDS), responseContent);
					throw new RuntimeException("Message not processed - HTTP error code : " + responseStatus);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}