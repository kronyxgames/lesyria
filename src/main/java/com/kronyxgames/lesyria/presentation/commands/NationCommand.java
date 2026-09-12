package com.kronyxgames.lesyria.presentation.commands;

import com.kronyxgames.lesyria.LesyriaPlugin;
import com.kronyxgames.lesyria.domain.city.City;
import com.kronyxgames.lesyria.domain.nation.Nation;
import com.kronyxgames.lesyria.domain.nation.NationMember;
import com.kronyxgames.lesyria.domain.nation.NationMemberView;
import com.kronyxgames.lesyria.domain.nation.NationRole;
import com.kronyxgames.lesyria.domain.nation.NationSummary;
import com.kronyxgames.lesyria.domain.territory.Territory;
import com.kronyxgames.lesyria.domain.player.PlayerProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /nation create|info|invite|join|leave|kick|setcapital|list|disband|deposit|withdraw|invites}.
 */
public final class NationCommand {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private NationCommand() {
    }

    /**
     * @param plugin plugin
     * @return le routeur de la commande
     */
    public static CommandRouter create(LesyriaPlugin plugin) {
        CommandRouter router = new CommandRouter(plugin, plugin.messages(), "nation",
                "lesyria.command.nation");

        router.sub("create", "<nom> <etiquette>", "Fonder une nation", true,
                        (sender, args) -> create(plugin, sender, args))
                .completer((sender, args) -> List.of("<nom>", "<ETQ>"));

        router.sub("info", "[nation]", "Informations sur une nation", true,
                        (sender, args) -> info(plugin, sender, args))
                .completer((sender, args) -> nationNames(plugin, args));

        router.sub("invite", "<joueur>", "Inviter un joueur", true,
                        (sender, args) -> invite(plugin, sender, args))
                .completer((sender, args) -> CommandSupport.onlinePlayerNames());

        router.sub("join", "<nation>", "Rejoindre une nation", true,
                        (sender, args) -> join(plugin, sender, args))
                .completer((sender, args) -> nationNames(plugin, args));

        router.sub("leave", "", "Quitter votre nation", true,
                (sender, args) -> leave(plugin, sender));

        router.sub("kick", "<joueur>", "Exclure un membre", true,
                        (sender, args) -> kick(plugin, sender, args))
                .completer((sender, args) -> memberNames(plugin, sender, args));

        router.sub("setcapital", "<ville>", "Definir la capitale", true,
                        (sender, args) -> setCapital(plugin, sender, args))
                .completer((sender, args) -> cityNames(plugin, sender, args));

        router.sub("list", "", "Lister les nations", true, (sender, args) -> list(plugin, sender));

        router.sub("invites", "", "Vos invitations en attente", true,
                (sender, args) -> invites(plugin, sender));

        router.sub("disband", "confirm", "Dissoudre votre nation",
                        "lesyria.command.nation.admin", true,
                        (sender, args) -> disband(plugin, sender, args));

        router.sub("deposit", "<montant>", "Deposer dans la tresorerie", true,
                (sender, args) -> deposit(plugin, sender, args));

        router.sub("withdraw", "<montant>", "Retirer de la tresorerie", true,
                (sender, args) -> withdraw(plugin, sender, args));

        return router;
    }

    private static void create(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 2, "/nation create <nom> <etiquette>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender,
                plugin.nations().create(profile, args[0], args[1]),
                nation -> messages.send(sender, "&aNation &e{name} {tag} &afondee.",
                        "name", nation.name(), "tag", nation.displayTag()));
    }

    private static void info(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Nation nation;
        if (args.length == 0) {
            Player player = CommandSupport.requirePlayer(sender);
            nation = plugin.nations().nationOf(player.getUniqueId());
            if (nation == null) {
                plugin.messages().error(sender,
                        "Vous n'appartenez a aucune nation. Utilisez &e/nation create <nom> <tag>&c.");
                return;
            }
        } else {
            nation = plugin.nations().cachedByName(args[0]);
            if (nation == null) {
                plugin.messages().error(sender, "Nation introuvable : &e{nation}", "nation", args[0]);
                return;
            }
        }
        var messages = plugin.messages();
        Nation target = nation;
        AsyncFeedback.when(plugin, messages, sender, plugin.nations().membersOf(target.id()),
                members -> display(sender, plugin, target, members));
    }

    private static void display(CommandSender sender, LesyriaPlugin plugin, Nation nation,
                                List<NationMemberView> members) {
        var messages = plugin.messages();
        List<Territory> territories = plugin.territories().territoriesOf(nation.id());
        City capital = nation.capitalCityId() == null ? null
                : plugin.cities().cachedById(nation.capitalCityId());

        messages.sendRaw(sender, "");
        messages.sendRaw(sender, "&6&l{tag} {name}", "tag", nation.displayTag(), "name", nation.name());
        messages.sendRaw(sender, "&7Chef: &f{leader}", "leader", leaderName(members));
        messages.sendRaw(sender, "&7Membres: &f{count} &7| Territoires: &f{territories}"
                        + " &7| Tresorerie: &e{treasury}",
                "count", Integer.toString(members.size()),
                "territories", Integer.toString(territories.size()),
                "treasury", messages.money(nation.treasury()));
        messages.sendRaw(sender, "&7Capitale: &f{capital}", "capital",
                capital == null ? "aucune" : capital.name());
        messages.sendRaw(sender, "&7Fondee le: &f{date} &7| Inscription: &f{join}",
                "date", DATE.format(nation.createdAt()),
                "join", nation.openJoin() ? "ouverte" : "sur invitation");
        if (!nation.description().isBlank()) {
            messages.sendRaw(sender, "&7Description: &f{description}",
                    "description", nation.description());
        }
        StringBuilder memberList = new StringBuilder();
        for (NationMemberView member : members) {
            if (memberList.length() > 0) {
                memberList.append("&7, ");
            }
            memberList.append("&f").append(member.username())
                    .append(" &8(").append(member.role().name().toLowerCase(Locale.ROOT)).append(")");
        }
        messages.sendRaw(sender, "&7Liste: {list}", "list", memberList.toString());
        messages.sendRaw(sender, "");
    }

    private static void invite(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/nation invite <joueur>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        PlayerProfile target = plugin.players().cachedByName(args[0]);
        var messages = plugin.messages();
        if (target == null) {
            messages.error(sender, "Ce joueur doit etre connecte pour etre invite.");
            return;
        }
        AsyncFeedback.whenComplete(plugin, messages, sender, plugin.nations().invite(actor, target),
                () -> {
                    messages.send(sender, "&aInvitation envoyee a &f{player}&a.",
                            "player", target.username());
                    Player online = plugin.getServer().getPlayer(target.uuid());
                    if (online != null) {
                        Nation nation = plugin.nations().nationOf(actor.uuid());
                        messages.send(online,
                                "&eVous avez ete invite a rejoindre &f{nation}&e. Utilisez &f/nation join {nation}",
                                "nation", nation == null ? "?" : nation.name());
                    }
                });
    }

    private static void join(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/nation join <nation>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender, plugin.nations().join(profile, args[0]),
                nation -> messages.send(sender, "&aBienvenue dans la nation &e{name} {tag}&a.",
                        "name", nation.name(), "tag", nation.displayTag()));
    }

    private static void leave(LesyriaPlugin plugin, CommandSender sender) {
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile profile = CommandSupport.requireProfile(plugin.players(), player);
        AsyncFeedback.whenComplete(plugin, plugin.messages(), sender,
                plugin.nations().leave(profile),
                () -> plugin.messages().send(sender, "&aVous avez quitte votre nation."));
    }

    private static void kick(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/nation kick <joueur>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender,
                CommandSupport.resolveProfile(plugin.players(), args[0]).thenCompose(target ->
                        plugin.nations().kick(actor, target).thenApply(ignored -> target)),
                target -> {
                    messages.send(sender, "&a{player} a ete exclu de la nation.",
                            "player", target.username());
                    Player online = plugin.getServer().getPlayer(target.uuid());
                    if (online != null) {
                        messages.send(online, "&cVous avez ete exclu de votre nation.");
                    }
                });
    }

    private static void setCapital(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/nation setcapital <ville>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender, plugin.nations().setCapital(actor, args[0]),
                nation -> messages.send(sender,
                        "&aCapitale de &e{name} &adefinie sur &e{city}&a.",
                        "name", nation.name(), "city", args[0]));
    }

    private static void list(LesyriaPlugin plugin, CommandSender sender) {
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender, plugin.nations().summaries(), summaries -> {
            if (summaries.isEmpty()) {
                messages.send(sender, "&7Aucune nation n'existe pour le moment.");
                return;
            }
            messages.send(sender, "&7Nations existantes ({count}) :",
                    "count", Integer.toString(summaries.size()));
            for (NationSummary summary : summaries) {
                Nation nation = summary.nation();
                messages.sendRaw(sender, " &8- &e{tag} &f{name} &7- {members} membres, "
                                + "{territories} territoires, tresorerie {treasury}",
                        "tag", nation.displayTag(), "name", nation.name(),
                        "members", Integer.toString(summary.memberCount()),
                        "territories", Integer.toString(summary.territoryCount()),
                        "treasury", messages.money(nation.treasury()));
            }
        });
    }

    private static void invites(LesyriaPlugin plugin, CommandSender sender) {
        Player player = CommandSupport.requirePlayer(sender);
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender,
                plugin.nations().invitedNations(player.getUniqueId()), nations -> {
                    if (nations.isEmpty()) {
                        messages.send(sender, "&7Vous n'avez aucune invitation en attente.");
                        return;
                    }
                    messages.send(sender, "&7Invitations en attente :");
                    for (Nation nation : nations) {
                        messages.sendRaw(sender, " &8- &e{name} {tag} &7: &f/nation join {name}",
                                "name", nation.name(), "tag", nation.displayTag());
                    }
                });
    }

    private static void disband(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        var messages = plugin.messages();
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            messages.error(sender,
                    "Cette action est irreversible. Confirmez avec &e/nation disband confirm&c.");
            return;
        }
        AsyncFeedback.whenComplete(plugin, messages, sender, plugin.nations().disband(actor),
                () -> messages.send(sender, "&aNation dissoute. Territoires et villes liberes."));
    }

    private static void deposit(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/nation deposit <montant>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        long amount = CommandSupport.parseAmount(args[0], plugin.economy().maxTransactionAmount());
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender, plugin.nations().deposit(actor, amount),
                record -> messages.send(sender, "&aDepot de &e{amount} &adans la tresorerie.",
                        "amount", messages.money(record.amount())));
    }

    private static void withdraw(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        CommandSupport.requireArgs(args, 1, "/nation withdraw <montant>");
        Player player = CommandSupport.requirePlayer(sender);
        PlayerProfile actor = CommandSupport.requireProfile(plugin.players(), player);
        long amount = CommandSupport.parseAmount(args[0], plugin.economy().maxTransactionAmount());
        var messages = plugin.messages();
        AsyncFeedback.when(plugin, messages, sender, plugin.nations().withdraw(actor, amount),
                record -> messages.send(sender, "&aRetrait de &e{amount} &ade la tresorerie.",
                        "amount", messages.money(record.amount())));
    }

    private static String leaderName(List<NationMemberView> members) {
        return members.stream()
                .filter(member -> member.role() == NationRole.LEADER)
                .map(NationMemberView::username)
                .findFirst()
                .orElse("inconnu");
    }

    private static List<String> nationNames(LesyriaPlugin plugin, String[] args) {
        String prefix = CommandSupport.lastArgument(args).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Nation nation : plugin.nations().all()) {
            if (nation.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(nation.name());
            }
        }
        return names;
    }

    private static List<String> memberNames(LesyriaPlugin plugin, CommandSender sender,
                                            String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        NationMember membership = plugin.nations().membershipOf(player.getUniqueId());
        if (membership == null) {
            return List.of();
        }
        String prefix = CommandSupport.lastArgument(args).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (java.util.UUID uuid : plugin.nations().cache().membersOf(membership.nationId())) {
            PlayerProfile profile = plugin.players().cached(uuid);
            if (profile != null && profile.username().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(profile.username());
            }
        }
        return names;
    }

    private static List<String> cityNames(LesyriaPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        NationMember membership = plugin.nations().membershipOf(player.getUniqueId());
        if (membership == null) {
            return List.of();
        }
        String prefix = CommandSupport.lastArgument(args).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (City city : plugin.cities().cachedOfNation(membership.nationId())) {
            if (city.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(city.name());
            }
        }
        return names;
    }
}
