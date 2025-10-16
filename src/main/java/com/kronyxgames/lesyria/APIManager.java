package com.kronyxgames.lesyria;

import io.javalin.Javalin;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class APIManager {

    private final LesyriaPlugin plugin;
    private final Gson gson = new Gson();
    private Javalin app;

    public APIManager(LesyriaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startAPI(int port) {
        app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(it -> it.anyHost());
            });
        }).start(port);

        // Endpoints
        app.get("/status", ctx -> {
            Map<String, Object> status = new HashMap<>();
            status.put("online", true);
            status.put("players", plugin.getServer().getOnlinePlayers().size());
            status.put("maxPlayers", plugin.getServer().getMaxPlayers());
            status.put("version", plugin.getServer().getVersion());
            ctx.json(status);
        });

        app.get("/news", ctx -> {
            Map<String, Object> news = new HashMap<>();
            news.put("title", "Welcome to Lesyria!");
            news.put("content", "Explore the Earth at 1:1 scale. New quests available!");
            news.put("date", "2025-10-16");
            ctx.json(news);
        });

        app.get("/economy/rates", ctx -> {
            Map<String, Double> rates = new HashMap<>();
            rates.put("diamond", 100.0);
            rates.put("iron_ingot", 20.0);
            rates.put("coal", 5.0);
            ctx.json(rates);
        });

        plugin.getLogger().info("Lesyria API started on port " + port);
    }

    public void stopAPI() {
        if (app != null) {
            app.stop();
            plugin.getLogger().info("Lesyria API stopped");
        }
    }
}