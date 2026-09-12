package com.kronyxgames.lesyria.infrastructure.http;

import com.google.gson.Gson;
import com.kronyxgames.lesyria.infrastructure.configuration.ConfigHolder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API HTTP publique en lecture seule.
 *
 * <p>Implementee avec le serveur HTTP du JDK : aucune dependance supplementaire
 * n'est necessaire pour exposer trois endpoints, et le plugin n'embarque pas un
 * serveur web complet.</p>
 *
 * <p>Endpoints : {@code GET /status}, {@code GET /news}. Aucune operation
 * d'administration n'est exposee ; l'adresse d'ecoute est configurable
 * ({@code 127.0.0.1} en production derriere un reverse proxy).</p>
 */
public final class StatusHttpServer {

    /**
     * Instantane du serveur expose par {@code GET /status}.
     *
     * @param online        serveur ouvert
     * @param players       joueurs connectes
     * @param maxPlayers    capacite
     * @param version       libelle de version (beta, release...)
     * @param uptimeSeconds duree de fonctionnement
     * @param nations       nombre de nations
     * @param territories   nombre de territoires
     * @param cities        nombre de villes
     * @param tps           ticks par seconde (1 minute)
     */
    public record StatusSnapshot(boolean online, int players, int maxPlayers, String version,
                                 long uptimeSeconds, int nations, int territories, int cities,
                                 double tps) {
    }

    private final ConfigHolder config;
    private final Logger logger;
    private final Supplier<StatusSnapshot> snapshotSupplier;
    private final Gson gson = new Gson();

    private HttpServer server;
    private ExecutorService executor;

    public StatusHttpServer(ConfigHolder config, Logger logger,
                            Supplier<StatusSnapshot> snapshotSupplier) {
        this.config = config;
        this.logger = logger;
        this.snapshotSupplier = snapshotSupplier;
    }

    /**
     * Demarre l'API si elle est activee en configuration.
     *
     * @return vrai si le serveur HTTP ecoute
     */
    public boolean start() {
        if (!config.api().enabled()) {
            logger.info("API HTTP desactivee par la configuration");
            return false;
        }
        try {
            InetSocketAddress address = new InetSocketAddress(config.api().bind(),
                    config.api().port());
            server = HttpServer.create(address, 0);
            server.createContext("/status", this::handleStatus);
            server.createContext("/news", this::handleNews);
            AtomicInteger counter = new AtomicInteger(1);
            executor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "lesyria-http-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            logger.info("API HTTP disponible sur http://" + config.api().bind() + ":"
                    + config.api().port() + "/status");
            return true;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "demarrage de l'API HTTP impossible (port "
                    + config.api().port() + " occupe ?)", ex);
            server = null;
            return false;
        }
    }

    /** Arrete l'API. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            logger.info("API HTTP arretee");
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** @return vrai si l'API ecoute. */
    public boolean isRunning() {
        return server != null;
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        StatusSnapshot snapshot = snapshotSupplier.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("online", snapshot.online());
        body.put("players", snapshot.players());
        body.put("maxPlayers", snapshot.maxPlayers());
        body.put("version", snapshot.version());
        body.put("uptimeSeconds", snapshot.uptimeSeconds());
        body.put("tps", Math.round(snapshot.tps() * 100.0d) / 100.0d);
        body.put("nations", snapshot.nations());
        body.put("territories", snapshot.territories());
        body.put("cities", snapshot.cities());
        respond(exchange, 200, gson.toJson(body));
    }

    private void handleNews(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        List<Map<String, String>> news = new java.util.ArrayList<>();
        for (var entry : config.api().news()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("title", entry.title());
            item.put("body", entry.body());
            item.put("date", entry.date());
            news.add(item);
        }
        respond(exchange, 200, gson.toJson(news));
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Allow", "GET");
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return false;
        }
        return true;
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        String corsOrigin = config.api().corsOrigin();
        if (corsOrigin != null && !corsOrigin.isBlank()) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", corsOrigin);
        }
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(payload);
        }
    }
}
