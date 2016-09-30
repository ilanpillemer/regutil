package org.gameontext.util.reg;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Rule;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.contrib.java.lang.system.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class RegistrationUtilityTest {
    private static final String HTTP_METHOD_ARG = "-m";
    private static final String GAMEON_ID = "-i";
    private static final String GAMEON_SECRET = "-s";
    private static final String MAP_SVC = "-u";
    private static final String ROOM_ID_ARG = "-r";

    static final String HMAC_ALGORITHM = "HmacSHA256";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule log = new SystemOutRule().enableLog();

    @Test
    public void test_show_help_when_no_args_provided() {
        exit.expectSystemExitWithStatus(1);
        RegistrationUtility.main(new String[] {} );
        String expected = String.join("\n"
                                      ,"GameOn registration utility\n"
                                      ,"Usage : <options> <path to registration json file>\n\nRequired parameters"
                                      ,"\t" + GAMEON_ID + "=<gameon id>\n\t" + GAMEON_SECRET + "=<gameon secret>"
                                      ,"\nOptional parameters"
                                      ,"\t" + MAP_SVC + "=<map service URL>"
                                      ,"\t" + ROOM_ID_ARG + "=<room ID>"
                                      ,"\t" + HTTP_METHOD_ARG + "=<HTTP method, defaults to POST if not specified>"
                                      ,""
                                      ,""
                                      );
        assertThat(log.getLog(),is(expected));
    }

    // fixes #9
    @Test
    public void test_POST_with_only_required_params() {
        exit.expectSystemExitWithStatus(500);
        RegistrationUtility.main(new String[] {"-i=123", "-s=dingdong","reg.json"} );
        assertThat(log.getLog(), containsString("POST"));
    }

    @Test
    public void test_DELETE_with_only_required_params() {
        exit.expectSystemExitWithStatus(500);
        RegistrationUtility.main(new String[] {"-i=123", "-s=dingdong","-m=DELETE","-r=123","reg.json"} );
        assertThat(log.getLog(), containsString("DELETE"));
    }

    // currently does not return an error code via command line use, perhaps should like other calls.
    @Test
    public void test_DELETE_without_room_id_throws_exception() {
        //exit.expectSystemExitWithStatus(500);
        RegistrationUtility.main(new String[] {"-i=123", "-s=dingdong","-m=DELETE","reg.json"} );
        assertThat(log.getLog(), containsString("you need to supply the room id"));
    }
    @Test
    public void test_PUT_with_only_required_params() {
        exit.expectSystemExitWithStatus(500);
        RegistrationUtility.main(new String[] {"-i=123", "-s=dingdong","-m=PUT","-r=123","reg.json"} );
        assertThat(log.getLog(), containsString("PUT"));
    }

    @Test
    public void test_GET_with_only_required_params() {
        exit.expectSystemExitWithStatus(404);
        RegistrationUtility.main(new String[] {"-i=123", "-s=dingdong","-m=GET","-r=123","reg.json"} );
        assertThat(log.getLog(), containsString("GET"));
    }


    // Room registration is a POST to the V1 MAP (Hard Coded).
    // The Payload is a JSON room description.
    // The headers include a HMAC signature, signed with a known "secret" between server and client.

    @Test
    public void test_room_registration() throws Exception {

        ArgumentCaptor<byte[]> jsonCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signatureCaptor = ArgumentCaptor.forClass(String.class);


        //Subject Under Test (sut)
        RegistrationUtility sut = new RegistrationUtility();
        sut.setId("id");
        sut.setSecret("secret");
        sut.setMethod(RegistrationUtility.HTTP_METHOD.POST);
        // Payload
        sut.setBody( String.join(System.getProperty("line.separator")
                                 ,"{"
                                 ,"    \"name\":\"roomShortname\","
                                 ,"    \"fullName\":\"Longer room name\","
                                 ,"    \"description\":\"Boring default room description. Note the target IP: This room will not be reachable from the live site without updates.\","
                                 ,"    \"doors\":{"
                                 ,"	\"s\":\"A winding path leading off to the south\","
                                 ,"	\"d\":\"A tunnel, leading down into the earth\","
                                 ,"	\"e\":\"An overgrown road, covered in brambles\","
                                 ,"	\"u\":\"A spiral set of stairs, leading upward into the ceiling\","
                                 ,"	\"w\":\"A shiny metal door, with a bright red handle\","
                                 ,"	\"n\":\"A Large doorway to the north\""
                                 ,"    },"
                                 ,"    \"connectionDetails\":{"
                                 ,"	\"type\":\"websocket\","
                                 ,"	\"target\":\"ws://127.0.0.1:9080/rooms/myRoom\""
                                 ,"    }"
                                 ,"}"
                                 ));

        // Hash of payload that should appear in header
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(sut.getBody().getBytes());
        String hash = Base64.getEncoder().encodeToString(md.digest());

        // Mocked connection so can inspect, verify, assert
        HttpURLConnection mocked = mock (HttpURLConnection.class);
        // Mocked output stream so can verify payload
        OutputStream stream= mock (OutputStream.class);
        when(mocked.getOutputStream()).thenReturn(stream);

        // The POST Registration Request should set the headers and add a signature
        // The signature should thus contain enough information so that client can
        // (1) Ensure payload and headers are untampered
        // (2) Can timeout
        // (3) Can prevent replays

        sut.sendToServer(mocked);
        verify(mocked).setDoInput(true); // this is default anyway, so shouldnt be needed.
        verify(mocked).setDoOutput(true);
        verify(mocked).setRequestMethod("POST");
        verify(mocked).setRequestProperty("Content-Type", "application/json;");
        verify(mocked).setRequestProperty("Accept", "application/json,text/plain");
        verify(mocked).setRequestProperty("gameon-id", "id");
        verify(mocked).getOutputStream();

        // this allows the client to expire these request based on a time limit (eg 5 minutes)
        verify(mocked).setRequestProperty(eq("gameon-date"), dateCaptor.capture());

        // hash of the payload, which is used as part of the signature generation.
        verify(mocked).setRequestProperty("gameon-sig-body", hash);

        // secure signature
        verify(mocked).setRequestProperty(eq("gameon-signature"),signatureCaptor.capture());

        // payload
        verify(stream).write(jsonCaptor.capture());
        // The POST payload should contain room json
        assertThat(new String(jsonCaptor.getValue()), is(sut.getBody()));

        // The signature should be a hash made up of method, uri, user id, date and payload
        // This signature will then allow the client to have a method to prevent replays by
        // comparing with a cache of recent requests
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(sut.getSecret().getBytes("UTF-8"),HMAC_ALGORITHM));
        mac.update("POST".getBytes());
        mac.update("/map/v1/sites".getBytes()); // harcoded in the utility currently
        mac.update("id".getBytes());
        mac.update(dateCaptor.getValue().getBytes());
        mac.update(hash.getBytes());
        String expectedSignature = Base64.getEncoder().encodeToString( mac.doFinal() );

        //If the client has the secret must be able to generate and compare one way hash
        assertThat(signatureCaptor.getValue(), is(expectedSignature));

        // POST connection should do nothing else
        verifyNoMoreInteractions(mocked);
    }

    // written to test refactoring to Java 8 syntax.
    @Test
    public void test_read_file() throws Exception{
        File riddle = folder.newFile("riddle");

        String tooFunny = String.join (System.getProperty("line.separator")
                                       ,"is green and stands behind a microphone?"
                                       ,"Elvis Parsely!");

        Files.write(riddle.toPath(),
                    tooFunny.getBytes(),
                    StandardOpenOption.APPEND);

        assertThat (RegistrationUtility.readFile(riddle.getAbsolutePath()),is(tooFunny));
    }
}
