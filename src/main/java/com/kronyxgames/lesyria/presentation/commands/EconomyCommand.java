package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.economy.EconomyActor;
import com.kronyxgames.lesyria.domain.economy.TransactionType;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /eco balance|pay|give|take|set}.
 *
 * <p>Les operations de joueur passent par le service economique : le solde est
 * verifie et les mouvements sont journalises. Les operations d'administration
 * exigent {@code lesyria.command.eco.admin}.</p>
 */
public final class EconomyCommand {

    private EconomyCommand() {
    }

    /**
     * @param plugin plugin
     * @return le routeur de la commande
     */
    public static CommandRouter create(LesyriaPlugin plugin) {
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), "eco",
                "lesyria.command.eco");

        router.sub("balance", "[joueur]", "Consulter votre solde", true,
                        (sender, args) -> balance(plugin, sender, args))
                .completer((sender, args) -> CommandSupport.onlinePlayerNames());

        router.sub("pay", "<joueur> <montant>", "Payer un joueur", true,
                        (sender, args) -> pay(plugin, sender, args))
                .completer((sender, args) -> args.length <= 1
                        ? CommandSupport.onlinePlayerNames() : List.of());

        router.sub("give", "<joueur> <montant>", "Crediter un joueur",
                        "lesyria.command.eco.admin", false,
                        (sender, args) -> adjust(plugin, sender, args, "give"))
                .completer((sender, args) -> args.length <= 1
                        ? CommandSupport.onlinePlayerNames() : List.of());

        router.sub("take", "<joueur> <montant>", "Debiter un joueur",
                        "lesyria.command.eco.admin", false,
                        (sender, args) -> adjust(plugin, sender, args, "take"))
                .completer((sender, args) -> args.length <= 1
                        ? CommandSupport.onlinePlayerNames() : List.of());

        router.sub("set", "<joueur> <montant>", "Fixer un solde",
                        "lesyria.command.eco.admin", false,
                        (sender, args) -> adjust(plugin, sender, args, "set"))
                .completer((sender, args) -> args.length <= 1
                        ? CommandSupport.onlinePlayerNames() : List.of());

        return router;
    }

    private static void balance(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        var messages = plugin.messages();
        if (args.length == 0) {
            PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
            messages.send(sender, "&7Solde : &e{balance}",
                    "balance", messages.money(profile.balance()));
            return;
        }
        PlayerProfile own = CommandSupport.requireProfile(plugin.players(), player);
        if (args[0].equalsIgnoreCase(player.getName())) {
            messages.send(sender, "&7Solde : &e{balance}", "balance", messages.money(own.balance()));
            return;
        }
        if (!sender.hasPermission("lesyria.command.eco.admin")) {
            messages.error(sender, "Vous n'avez pas la permission de consulter un autre solde.");
            return;
        }
        AsyncFeedback.when(plugin, messages, sender,
                CommandSupport.resolveProfile(plugin.players(), args[0]), target -> messages.send(
                sender, "&7Solde de &f{player} &7: &e{balance}",
                "player", target.username(), "balance", messages.money(target.balance())));
    }

    private static void pay(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        CommandSupport.requireArgs(args, 2, "/eco pay <joueur> <montant>");
        long amount = CommandSupport.parseAmount(args[1], plugin.economy().maxTransactionAmount());
        PlayerProfile payer = CommandSupport.requireProfile(plugin.players(), player);

        AsyncFeedback.when(plugin, plugin.messages(), sender,
                CommandSupport.resolveProfile(plugin.players(), args[0]).thenCompose(target -> plugin.economy()
                        .payPlayer(payer.uuid(), target.uuid(), amount, "paiement de joueur")
                        .thenApply(record -> new Payment(target, amount, record.amount()))),
                payment -> {
                    var messages = plugin.messages();
                    messages.send(sender, "&aVous avez paye &e{amount} &a a &f{player}&a.",
                            "amount", messages.money(payment.paid()),
                            "player", payment.target().username());
                    Player online = plugin.getServer().getPlayer(payment.target().uuid());
                    if (online != null) {
                        messages.send(online, "&aVous avez recu &e{amount} &ade &f{payer}&a.",
                                "amount", messages.money(payment.received()),
                                "payer", payer.username());
                    }
                });
    }

    private static void adjust(LesyriaPlugin plugin, CommandSender sender, String[] args,
                               String mode) {
        CommandSupport.requireArgs(args, 2, "/eco " + mode + " <joueur> <montant>");
        long amount = CommandSupport.parseAmount(args[1], plugin.economy().maxTransactionAmount());
        var messages = plugin.messages();
        String author = sender.getName();

        CompletableFuture<String> operation =
                CommandSupport.resolveProfile(plugin.players(), args[0]).thenCompose(target ->
                switch (mode) {
                    case "give" -> plugin.economy().credit(EconomyActor.player(target.uuid()), amount,
                                    TransactionType.ADMIN_GIVE, "commande admin de " + author)
                            .thenApply(record -> "&aCredit de &e" + messages.money(amount)
                                    + " &a a &f" + target.username());
                    case "take" -> plugin.economy().debit(EconomyActor.player(target.uuid()), amount,
                                    TransactionType.ADMIN_TAKE, "commande admin de " + author)
                            .thenApply(record -> "&aDebit de &e" + messages.money(amount)
                                    + " &a a &f" + target.username());
                    default -> plugin.economy().setBalance(EconomyActor.player(target.uuid()), amount,
                                    "commande admin de " + author)
                            .thenApply(result -> result.map(record -> "&aSolde de &f"
                                            + target.username() + " &afixe a &e"
                                            + messages.money(amount))
                                    .orElse("&7Le solde etait deja de &e" + messages.money(amount)));
                });

        AsyncFeedback.when(plugin, messages, sender, operation,
                message -> messages.send(sender, message));
    }

    private record Payment(PlayerProfile target, long paid, long received) {
    }
}
