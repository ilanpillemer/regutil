/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package net.wasdev.gameon.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

public class RegistrationUtility {
	
	private enum HTTP_METHOD { GET, PUT, POST, DELETE};
	
	private static final Map<String, String> cmdargs = new HashMap<String, String>();
	private static String roomid = null;
	private static String body = null;
	private static String url = null;
	private static HTTP_METHOD method = HTTP_METHOD.POST;
	
	private static final String HTTP_METHOD_ARG = "-m";
	private static final String GAMEON_ID = "-i";
	private static final String GAMEON_SECRET = "-s";
	private static final String MAP_SVC = "-u";
	private static final String ROOM_ID_ARG = "-r";
	
	public static void main(String[] args) {
		try {
			parseArgs(args);
			url = cmdargs.containsKey(MAP_SVC) ? cmdargs.get(MAP_SVC) : "https://game-on.org/map/v1/sites";
			switch(method) {
				case POST:
					register();
					break;
				case PUT:
					update();
					break;
				case DELETE:
					delete();
					break;
				case GET:
					details();
					break;
				
			}
		} catch (Exception e) {
			System.out.println("Error : " + e.getMessage());
			printHelp();
		}
	}
	
	public static void parseArgs(String[] args) throws Exception {
		if(args.length == 0) {
			printHelp();
			System.exit(1);
		}
		for(int i = 0; i < args.length - 1; i++) {
			int pos = args[i].indexOf('=');	//split this way as value may contain = at the end e.g. for B64 encoded string
			String key = null;
			String value = null;
			if(pos == -1) {
				key = args[i];
			} else {
				key = args[i].substring(0, pos);
				value = args[i].substring(pos + 1);
			}
			if(cmdargs.put(key, value) != null) {
				System.out.println("Warning : duplicate argument specified - " + key);
			}
		}
		if(!cmdargs.containsKey(GAMEON_ID) || !cmdargs.containsKey(GAMEON_SECRET)) {
			throw new IllegalArgumentException("Missing required options");
		}
		if(cmdargs.containsKey(HTTP_METHOD_ARG)) {
			method = HTTP_METHOD.valueOf(cmdargs.get(HTTP_METHOD_ARG));
		}
		switch(method) {
			case PUT:
				if(cmdargs.containsKey(ROOM_ID_ARG)) {
					roomid = cmdargs.get(ROOM_ID_ARG);
				} else {
					throw new IllegalArgumentException("When specifying an update with PUT, you need to supply the room id with -r");
				}
				//allow to fall through to read file contents for update
			case POST:
				String path = args[args.length -1];
				body = readFile(path);
				break;
			default:
				roomid = args[args.length -1];
				break;
		}
	}
	
	private static String readFile(String path) throws Exception {
		File file = new File(path);
		if(!file.exists() || !file.isFile()) {
			throw new IllegalArgumentException("Invalid path for registration JSON file specified : " + path); 
		}
		FileReader reader = new FileReader(file);
		char[] contents = new char[(int)file.length()];
		reader.read(contents);
		reader.close();
		return new String(contents).trim();
	}
	
	private static void printHelp() {
		System.out.println("GameOn registration utility\n");
		System.out.println("Usage : <options> <path to registration json file>\n\nRequired parameters");
		System.out.println("\t" + GAMEON_ID + "=<gameon id>\n\t" + GAMEON_SECRET + "=<gameon secret>");
		System.out.println("\nOptional parameters");
		System.out.println("\t" + MAP_SVC + "=<map service URL>");
		System.out.println("\t" + ROOM_ID_ARG + "=<room ID>");
		System.out.println("\t" + HTTP_METHOD_ARG + "=<HTTP method, defaults to POST if not specified>\n");
	}

	//DELETE
	private static void delete() throws Exception {
		System.out.println("Starting room deletion for Room ID : " + roomid);
        String bodyHash = SecurityUtils.buildHash("");
        HttpURLConnection con = sendToServer(url + "/" + roomid,  bodyHash);

        System.out.println("Deletion gave http code: " + con.getResponseCode() + " " + con.getResponseMessage());
        getJSONResponse(con);
	}
	
	//POST
	private static void register() throws Exception {
        System.out.println("Beginning room registration / update.");
        String bodyHash = SecurityUtils.buildHash(body);
        HttpURLConnection con = sendToServer(url, bodyHash);

        System.out.println("Registration gave http code: " + con.getResponseCode() + " " + con.getResponseMessage());
        getJSONResponse(con);
    }
	
	//PUT
	private static void update() throws Exception {
		System.out.println("Starting room update for Room ID : " + roomid);
        String bodyHash = SecurityUtils.buildHash(body);
        HttpURLConnection con = sendToServer(url + "/" + roomid,  bodyHash);

        System.out.println("Update gave http code: " + con.getResponseCode() + " " + con.getResponseMessage());
        getJSONResponse(con);
	}
	
	//GET
	private static void details() throws Exception {
		System.out.println("Getting room details for Room ID : " + roomid);
        String bodyHash = SecurityUtils.buildHash("");
        HttpURLConnection con = sendToServer(url + "/" + roomid, bodyHash);

        System.out.println("Server gave http code: " + con.getResponseCode() + " " + con.getResponseMessage());
        getJSONResponse(con);
	}
	
	private static HttpURLConnection sendToServer(String url, String bodyHash) throws Exception {
		System.out.println("Connecting to GameOn! at " + url + "\n");
		URL u = new URL(url);
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        if(url.startsWith("https://")) {
            ((HttpsURLConnection)con).setHostnameVerifier(new TheNotVerySensibleHostnameVerifier());
        }
        
        String userId = cmdargs.get(GAMEON_ID);
        String key = cmdargs.get(GAMEON_SECRET);
        
        Instant now = Instant.now();
        String dateValue = now.toString();
        
        String hmac = SecurityUtils.buildHmac(Arrays.asList(new String[] {
                userId,
                dateValue,
                bodyHash
        }),key);
        
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setRequestProperty("Content-Type", "application/json;");
        con.setRequestProperty("Accept", "application/json,text/plain");
        con.setRequestProperty("Method", method.name());
        con.setRequestProperty("gameon-id", userId);
        con.setRequestProperty("gameon-date", dateValue);
        con.setRequestProperty("gameon-sig-body", bodyHash);
        con.setRequestProperty("gameon-signature", hmac);
        con.setRequestMethod(method.name());
        
        if(body != null) {
        	OutputStream os = con.getOutputStream();
        	os.write(body.getBytes("UTF-8"));
        	os.close();
        }

        System.out.println("Method:" + method);
        System.out.println("gameon-id:" + userId);
        System.out.println("gameon-sig-body:" + bodyHash);
        System.out.println("gameon-date:" + dateValue);
        System.out.println("gameon-signature:" + hmac + "\n");
        
        return con;
	}
    
    private static String getJSONResponse(HttpURLConnection con) throws Exception {
    	InputStream stream =  (con.getResponseCode() >= HttpURLConnection.HTTP_OK) || (con.getResponseCode() <= HttpURLConnection.HTTP_NO_CONTENT) ? con.getInputStream() : con.getErrorStream(); 
        try (BufferedReader buffer = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"))) {
            String response = buffer.lines().collect(Collectors.joining("\n"));
            System.out.println("Response from server.");
            System.out.println(response);
            return response;
        }
    }
	
}
