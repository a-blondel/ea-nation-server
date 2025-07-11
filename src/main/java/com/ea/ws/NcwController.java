package com.ea.ws;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@RestController
public class NcwController {

    /**
     * Handles HTTP POST requests for the NWC "/ac" endpoint.
     * It supports the "login" and "svcloc" actions.
     * Decodes the incoming request parameters, processes the data,
     * and returns an encoded response.
     *
     * @param body a map of request parameters, expected to include an "action" key
     * @return a ResponseEntity containing the processed response data as a string
     */
    @PostMapping(value = "/ac", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> ac(@RequestParam Map<String, String> body) {
        Map<String, String> decodedBody = decode(body);
        Map<String, String> response = processData(decodedBody);
        return ResponseEntity.ok(encode(response));
    }

    /**
     * Process the request data and return a map of response parameters.
     *
     * @param body a map of request parameters, expected to include an "action" key
     * @return a map of response parameters
     */
    private Map<String, String> processData(Map<String, String> body) {
        Map<String, String> response = new HashMap<>();
        String action = body.get("action");
        String authToken = generateAuthToken();
        if ("login".equals(action)) {
            String challenge = generateRandomStr(8);
            response.put("retry", "0");
            response.put("returncd", "001");
            response.put("locator", "gamespy.com");
            response.put("challenge", challenge);
            response.put("token", authToken);
        } else if ("svcloc".equals(action)) {
            response.put("retry", "0");
            response.put("returncd", "007");
            response.put("statusdata", "Y");
            if (body.containsKey("svc")) {
                if (body.get("svc").equals("9000") || body.get("svc").equals("9001")) {
                    response.put("svchost", body.get("host").split(",")[0]);
                    if (body.get("svc").equals("9000")) {
                        response.put("token", authToken);
                    } else {
                        response.put("servicetoken", authToken);
                    }
                } else if (body.get("svc").equals("0000")) {
                    response.put("servicetoken", authToken);
                    response.put("svchost", "n/a");
                } else {
                    response.put("svchost", "n/a");
                    response.put("servicetoken", authToken);
                }
            }
        }
        return response;
    }

    /**
     * Generate a random string of alphanumeric characters of the given length.
     * @param length The length of the string to generate.
     * @return A random string of alphanumeric characters of the given length.
     */
    private String generateRandomStr(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a random authentication token.
     * <p>
     * The generated token is a random string of 80 characters, prefixed with "NDS".
     * <p>
     *
     * @return the generated authentication token
     */
    private String generateAuthToken() {
        return "NDS" + generateRandomStr(80);
    }

    /**
     * Decodes a map of strings that were encoded by the game client.
     * <p>
     * The game client encodes the strings in the following way:
     * <ul>
     *     <li>It URL encodes the string.</li>
     *     <li>It replaces certain characters with others.</li>
     *     <li>It base64 encodes the string.</li>
     * </ul>
     * <p>
     * This method reverses the above process.
     *
     * @param map the map of strings to decode
     * @return the decoded map of strings
     */
    private Map<String, String> decode(Map<String, String> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> {
                            String value = e.getValue();
                            if (value.endsWith("%")) {
                                value = value.substring(0, value.length() - 1);
                            }
                            value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                            value = value.replace("*", "=")
                                    .replace("?", "/")
                                    .replace(">", "+")
                                    .replace("-", "/");
                            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                        }));
    }

    /**
     * Encodes a map of strings in the same way that the game client does.
     * <p>
     * The game client encodes the strings in the following way:
     * <ul>
     *     <li>It base64 encodes the string.</li>
     *     <li>It replaces any '=' characters in the base64 string with '*'</li>
     *     <li>It joins the encoded strings with '&'</li>
     * </ul>
     * <p>
     * This method reverses the above process.
     *
     * @param map the map of strings to encode
     * @return the encoded map of strings
     */
    private String encode(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e ->
                        e.getKey()
                                + "="
                                + Base64.getEncoder().encodeToString(e.getValue().getBytes())
                                .replace("=", "*"))
                .collect(Collectors.joining("&"));
    }

}
