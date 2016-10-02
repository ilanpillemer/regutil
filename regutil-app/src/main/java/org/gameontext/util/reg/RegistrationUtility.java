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
package org.gameontext.util.reg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.gameontext.signed.SignedRequestHmac;
import org.gameontext.signed.SignedRequestMap;

public class RegistrationUtility {

    public enum HTTP_METHOD { GET, PUT, POST, DELETE};

    private static final Map<String, String> cmdargs = new HashMap<String, String>();
    private String roomid = null;
    private String body = "";
    private String url = null;
    private HTTP_METHOD method = HTTP_METHOD.POST;

    private static final String HTTP_METHOD_ARG = "-m";
    private static final String GAMEON_ID = "-i";
    private static final String GAMEON_SECRET = "-s";
    private static final String MAP_SVC = "-u";
    private static final String ROOM_ID_ARG = "-r";

    public static void main(String[] args) {
        try {
            RegistrationUtility util = new RegistrationUtility();
            parseArgs(args, util);
            util.url = cmdargs.containsKey(MAP_SVC) ? cmdargs.get(MAP_SVC) : "https://game-on.org/map/v1/sites";
            int resCode = 0; //Server HTTP response code
            switch(util.method) {
            case POST:
                resCode = util.register();
                break;
            case PUT:
                resCode = util.update();
                break;
            case DELETE:
                resCode = util.delete();
                break;
            case GET:
                resCode = util.details();
                break;
            }
            //convert the HTTP response code into a system exit for build systems
            int exitCode = (resCode >= HttpURLConnection.HTTP_OK) && (resCode <= HttpURLConnection.HTTP_NO_CONTENT) ? 0 : resCode;
            System.out.println("System exit code : " + exitCode);
            System.exit(exitCode);
        } catch (Exception e) {
            System.out.println("Error : " + e.getMessage());
            printHelp();
        }
    }

    private static void parseArgs(String[] args, RegistrationUtility util) throws Exception {
        if(args.length == 0) {
            printHelp();
            System.exit(1);
        }
        for(int i = 0; i < args.length - 1; i++) {
            int pos = args[i].indexOf('=');     //split this way as value may contain = at the end e.g. for B64 encoded string
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
            util.method = HTTP_METHOD.valueOf(cmdargs.get(HTTP_METHOD_ARG));
        }
        switch(util.method) {
        case PUT:
            if(cmdargs.containsKey(ROOM_ID_ARG)) {
                util.roomid = cmdargs.get(ROOM_ID_ARG);
            } else {
                throw new IllegalArgumentException("When specifying an update with PUT, you need to supply the room id with -r");
            }
            //allow to fall through to read file contents for update
        case POST:
            String path = args[args.length -1];
            util.body = readFile(path);
            break;
        default:
            util.roomid = args[args.length -1];
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

    //Configuration options for invoking via code

    public String getRoomid() {
        return roomid;
    }

    public void setRoomid(String roomid) {
        this.roomid = roomid;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public HTTP_METHOD getMethod() {
        return method;
    }

    public void setMethod(HTTP_METHOD method) {
        this.method = method;
    }

    public String getId() {
        return cmdargs.get(GAMEON_ID);
    }

    public void setId(String id) {
        cmdargs.put(GAMEON_ID, id);
    }

    public String getSecret() {
        return cmdargs.get(GAMEON_SECRET);
    }

    public void setSecret(String secret) {
        cmdargs.put(GAMEON_SECRET, secret);
    }

    //DELETE
    public int delete() throws Exception {
        System.out.println("Starting room deletion for Room ID : " + roomid);
        HttpURLConnection con = sendToServer(url + "/" + roomid);
        return getJSONResponse(con);
    }

    //POST
    public int register() throws Exception {
        System.out.println("Beginning room registration.");
        HttpURLConnection con = sendToServer(url);
        return getJSONResponse(con);
    }

    //PUT
    public int update() throws Exception {
        System.out.println("Starting room update for Room ID : " + roomid);
        HttpURLConnection con = sendToServer(url + "/" + roomid);
        return getJSONResponse(con);
    }

    //GET
    public int details() throws Exception {
        System.out.println("Getting room details for Room ID : " + roomid);
        HttpURLConnection con = sendToServer(url + "/" + roomid);
        return getJSONResponse(con);
    }

    private HttpURLConnection sendToServer(String url) throws Exception {
        System.out.println("Connecting to GameOn! at " + url + "\n");
        URL u = new URL(url);
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        if(url.startsWith("https://")) {
            ((HttpsURLConnection)con).setHostnameVerifier(new TheNotVerySensibleHostnameVerifier());
        }

        String userId = cmdargs.get(GAMEON_ID);
        String key = cmdargs.get(GAMEON_SECRET);

        con.setRequestMethod(method.name());

        if(!method.equals(HTTP_METHOD.GET)) {
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json;");
            con.setRequestProperty("Accept", "application/json,text/plain");

            MultivaluedMap<String, Object> hmacHeaders = new MultivaluedHashMap<>();
            SignedRequestMap headers = new SignedRequestMap.MVSO_StringMap(hmacHeaders);
            String baseuri = (roomid != null) ? "/map/v1/sites/" + roomid : "/map/v1/sites";
            //all methods except GET need to be authenticated
            SignedRequestHmac clientHmac = new SignedRequestHmac(userId, key, method.name(), baseuri);
            clientHmac.generateBodyHash(headers, body.getBytes("UTF-8"));
            clientHmac.signRequest(headers);
            clientHmac.getSignature();

            for(String header : headers.keySet()) {
                String value = headers.getAll(header, "");
                con.setRequestProperty(header, value);
                System.out.println(header + ":" + value);
            }

            if(body != null) {
                OutputStream os = con.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
        } else {
            con.setDoInput(true);
        }
        return con;
    }

    private int getJSONResponse(HttpURLConnection con) throws Exception {
        int resCode = con.getResponseCode();
        int exitCode = (resCode >= HttpURLConnection.HTTP_OK) && (resCode <= HttpURLConnection.HTTP_NO_CONTENT) ? 0 : resCode;
        System.out.println("Response from server. (code = " + resCode + ")");
        try {
            InputStream stream =  (exitCode == 0) ? con.getInputStream() : con.getErrorStream();
            if (stream != null) {
                try (BufferedReader buffer = new BufferedReader(
                                                                new InputStreamReader(stream, "UTF-8"))) {
                    String response = buffer.lines().collect(Collectors.joining("\n"));
                    System.out.println(response);
                }
            }
        } catch (IOException e) {
            System.out.println("The server did not supply any additional information.");
        }
        return resCode;
    }
}
