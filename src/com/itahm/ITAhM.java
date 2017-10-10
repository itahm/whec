package com.itahm;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.itahm.http.Listener;
import com.itahm.http.Request;
import com.itahm.http.Response;
import com.itahm.http.Session;
import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

public class ITAhM extends Listener implements Closeable {
	
	private final static String DATA_DIR = "data";
	private final static String CONFIG_FILE = "config";
	private final static String MD5_ITAHM = "e9a44b8efe3bf7d33e105338792696c6";
	public static long expire = 0;
	
	private final Syslog server;
	
	private File root;
	
	public ITAhM() throws Exception {
		super("0.0.0.0", 2014);
		
		System.out.format("ITAhM communicator started with TCP %d.\n", 2014);
		
		root = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
		root = new File("F:\\ITAhM\\project\\demo\\whec");
		System.out.format("Directory : %s\n", root.getAbsoluteFile());
		
		File dataRoot = new File(root, DATA_DIR);
		
		dataRoot.mkdir();
		
		File confFile = new File(root, CONFIG_FILE);
		
		if (!confFile.isFile()) {
			try (FileOutputStream fos = new FileOutputStream(confFile)) {
				fos.write(new JSONObject().toString().getBytes(StandardCharsets.UTF_8));
			}
		}
		
		System.out.format("Agent loading...\n");
		
		server = new Syslog(dataRoot);
		
		System.out.format("ITAhM Syslog running.\n");
	}
		
	public static JSONObject getJSONFromFile(File file) throws IOException {
		try {
			return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		}
		catch (JSONException jsone) {
			return null;
		}
	}
	
	private Response parseRequest(Request request) throws IOException{
		if (!"HTTP/1.1".equals(request.getRequestVersion())) {
			return Response.getInstance(Response.Status.VERSIONNOTSUP);
		}
		
		switch(request.getRequestMethod()) {
		case "OPTIONS":
			return Response.getInstance(Response.Status.OK).setResponseHeader("Allow", "GET, POST");
		
		case"POST":
			try {
				JSONObject data = new JSONObject(new String(request.getRequestBody(), StandardCharsets.UTF_8.name()));
				
				if (!data.has("command")) {
					return Response.getInstance(Response.Status.BADREQUEST
						, new JSONObject().put("error", "command not found").toString());
				}
				
				return executeRequest(request, data);
			} catch (JSONException e) {e.printStackTrace();
				return Response.getInstance(Response.Status.BADREQUEST
					, new JSONObject().put("error", "invalid json request").toString());
			} catch (UnsupportedEncodingException e) {
				return Response.getInstance(Response.Status.BADREQUEST
					, new JSONObject().put("error", "UTF-8 encoding required").toString());
			} catch (Exception e) {
				return Response.getInstance(Response.Status.SERVERERROR);
			}
		}
		
		return Response.getInstance(Response.Status.NOTALLOWED).setResponseHeader("Allow", "OPTIONS, POST, GET");
	}
	
	public Response executeRequest(Request request, JSONObject data) throws IOException {
		String cmd = data.getString("command");
		Session session = getSession(request);
		
		if ("signin".equals(cmd)) {
			if (session == null) {
				try {
					if (getPassword().equals(data.getString("password"))) {
						session = Session.getInstance(0);
					}
				} catch (JSONException jsone) {
					return Response.getInstance(Response.Status.BADREQUEST
						, new JSONObject().put("error", "invalid json request").toString());
				}
			}
			
			if (session == null) {
				return Response.getInstance(Response.Status.UNAUTHORIZED);
			}
			
			return Response.getInstance(Response.Status.OK,
				new JSONObject().put("level", (int)session.getExtras()).toString())
				.setResponseHeader("Set-Cookie", String.format("SESSION=%s; HttpOnly", session.getCookie()));
		}
		else if ("signout".equals(cmd)) {
			if (session != null) {
				session.close();
			}
			
			return Response.getInstance(Response.Status.OK);
		}
		
		if (session == null) {
			return Response.getInstance(Response.Status.UNAUTHORIZED);
		}
		
		switch(cmd) {
		case "host":
			return Response.getInstance(Response.Status.OK
					, this.server.getHost(data.getLong("date")));
		
		case "log":
			return Response.getInstance(Response.Status.OK
					, this.server.getLog(data.getString("ip"), data.getLong("date")));
		
		case "config":
			return Response.getInstance(Response.Status.OK, getConfig().toString());
		
		case "password":
			if (getPassword().equals(data.getString("password"))) {
				setPassword(data.getString("password2"));
				
				return Response.getInstance(Response.Status.OK);
			}
			else {
				return Response.getInstance(Response.Status.UNAUTHORIZED);
			}
		}
		
		return Response.getInstance(Response.Status.BADREQUEST);
	}
	
	private static Session getSession(Request request) {
		String cookie = request.getRequestHeader(Request.Header.COOKIE);
		
		if (cookie == null) {
			return null;
		}
		
		String [] cookies = cookie.split("; ");
		String [] token;
		Session session = null;
		
		for(int i=0, length=cookies.length; i<length; i++) {
			token = cookies[i].split("=");
			
			if (token.length == 2 && "SESSION".equals(token[0])) {
				session = Session.find(token[1]);
				
				if (session != null) {
					session.update();
				}
			}
		}
		
		return session;
	}
	
	private String getPassword() throws IOException {
		JSONObject config = getJSONFromFile(new File(root, CONFIG_FILE));
		
		return config.has("password")? config.getString("password"): MD5_ITAHM;
	}
	
	private JSONObject getConfig() throws IOException {
		JSONObject config = getJSONFromFile(new File(root, CONFIG_FILE));
		
		config.remove("password");
		
		return config;
	}
	
	private void setPassword(String password) throws IOException {
		File file = new File(root, CONFIG_FILE);
		JSONObject config = getJSONFromFile(file);
		
		config.put("password", password);
		
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(config.toString().getBytes(StandardCharsets.UTF_8));
		}
	}
	
	@Override
	public void close() {
		try {			
			this.server.close();
			
			super.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		System.out.format("ITAhM Syslog, since 2014.\n");
		
		try {
			final ITAhM itahm = new ITAhM();
			
			Runtime.getRuntime().addShutdownHook(
				new Thread() {
					public void run() {System.out.println("!");
						itahm.close();
					}
				});
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onStart() {
		System.out.println("HTTP Server start.");
	}

	@Override
	protected void onRequest(Request request) throws IOException {
		String origin = request.getRequestHeader(Request.Header.ORIGIN);
		Response response = parseRequest(request);
		
		if (origin == null) {
			origin = "http://itahm.com";
		}
	
		response.setResponseHeader("Access-Control-Allow-Origin", origin);
		response.setResponseHeader("Access-Control-Allow-Credentials", "true");
		
		request.sendResponse(response);
	}

	@Override
	protected void onClose(Request request) {
	}

	@Override
	protected void onException(Exception e) {
	}
	
}
