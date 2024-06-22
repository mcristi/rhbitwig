package com.rhcommons;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

import com.rhcommons.json.JsonObject;
import com.rhcommons.json.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.yaeltex.djcontroller.DjControllerExtension;

public class HttpJsonHandler implements HttpHandler {
    
    private final TraktorState traktorState;
    
    public HttpJsonHandler(final TraktorState traktorState) {
        this.traktorState = traktorState;
    }
    
    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            final ByteArrayOutputStream result = new ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            for (int length; (length = exchange.getRequestBody().read(buffer)) != -1; ) {
                result.write(buffer, 0, length);
            }
            final String info = result.toString();
            if (info.startsWith("{\"deckId\"")) {
                final JsonObject data = new JsonParser(info).parse();
                final Optional<String> deck = data.getStringValue("deckId");
                deck.ifPresent(deckId -> {
                    final int deckIndex = deckId.charAt(0) - 'A';
                    final JsonObject metadata = data.getJsonObject("metadata");
                    if (metadata != null) {
                        DjControllerExtension.println(
                            " DECK %s %d => %s", deckId, deckIndex, metadata.getStringValue("key").orElse("ERR"));
                        traktorState.setKey(deckIndex, metadata.getStringValue("key").orElse("ERR"));
                    }
                });
            }
            final OutputStream outputStream = exchange.getResponseBody();
            exchange.sendResponseHeaders(200, 0);
            outputStream.flush();
            outputStream.close();
        } else {
            DjControllerExtension.println(" OTHER %s", exchange.getRequestMethod());
        }
    }
    
    
}
