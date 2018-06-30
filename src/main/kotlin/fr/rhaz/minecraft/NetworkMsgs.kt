package fr.rhaz.minecraft

import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.config.ServerInfo
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.PlayerDisconnectEvent
import net.md_5.bungee.api.event.ServerSwitchEvent
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.config.Configuration
import net.md_5.bungee.config.ConfigurationProvider
import net.md_5.bungee.config.YamlConfiguration
import net.md_5.bungee.event.EventHandler
import net.md_5.bungee.event.EventPriority
import java.io.File
import java.nio.file.Files
import java.util.logging.Logger

fun text(string: String) = TextComponent(string.replace("&", "§"))
fun CommandSender.msg(msg: String) = sendMessage(text(msg))

fun Logger.donate(proxy: ProxyServer) {
    val file = File(".noads")
    if(file.exists()) return
    val plugins = proxy.pluginManager.plugins
            .filter { plugin -> listOf("Hazae41", "RHazDev").contains(plugin.description.author) }
            .map { plugin -> plugin.description.name }
    this.info("""
    |
    |     __         _    ____  __   ___
    |    |__) |__|  /_\   ___/ |  \ |__  \  /
    |	|  \ |  | /   \ /___  |__/ |___  \/
    |
    |   It seems you use $plugins
    |
    |   If you like my softwares or you just want to support me, I'd enjoy donations.
    |   By donating, you're going to encourage me to continue developing quality softwares.
    |   And you'll be added to the donators list!
    |
    |   Click here to donate: http://dev.rhaz.fr/donate
    |
    """.trimMargin("|"))
    file.createNewFile()
    Thread{Thread.sleep(1000); file.delete()}.start()
}

class NetworkMsgs : Plugin() {

    lateinit var config: Configuration
    var permission: Boolean = false

    lateinit var playersConf: Configuration
    lateinit var silents: MutableList<String>
    lateinit var players: MutableList<String>

    var servers = HashMap<ProxiedPlayer, ServerInfo>()

    override fun onEnable() {
        this.proxy.pluginManager.registerListener(this, listener)
        this.proxy.pluginManager.registerCommand(this, cmd)
        reload()
        logger.donate(proxy)
    }

    fun reload() {
        config = loadConfig("config.yml") ?: return
        permission = config.getBoolean("use-permission")
        playersConf = loadConfig("players.yml") ?: return
        players = playersConf.getStringList("players")
        silents = playersConf.getStringList("silent")
    }

    val provider = ConfigurationProvider.getProvider(YamlConfiguration::class.java)!!
    fun loadConfig(name: String): Configuration? = null.also{
        if (!dataFolder.exists())
            dataFolder.mkdir()

        val file = File(dataFolder, name)

        try {
            if (!file.exists())
                Files.copy(getResourceAsStream(name), file.toPath())

            return provider.load(file)
        } catch (e: Exception) { e.printStackTrace() }
    }
    fun saveConfig(config: Configuration, file: String) {
        try {
            provider.save(config, File(dataFolder, file))
        } catch (e: Exception) { e.printStackTrace() }
    }

    val listener = object: Listener{

        @EventHandler(priority = EventPriority.HIGH)
        fun onConnection(e: ServerSwitchEvent) {

            if(servers.containsKey(e.player))
                return
            if(silents.contains(e.player.name))
                return

            val toServer = e.player.server.info.name
            val toPlayers = e.player.server.info.players
            val allPlayers = HashSet<ProxiedPlayer>(proxy.players)
            allPlayers.removeAll(toPlayers)

            listOf("welcome", "join-to", "join-all").forEach{

                var msg = config.getString(it)

                if(it == "welcome") {
                    if (players.contains(e.player.name))
                        return@forEach
                    players.add(e.player.name)
                    playersConf.set("players", players)
                    saveConfig(playersConf, "players.yml")
                }

                else if(it == "join-to")
                    if (config.getSection("servers").keys.contains(toServer))
                        msg = config.getSection("servers.$toServer").getString(it)

                if(msg.isEmpty())
                    return@forEach

                val players = when(it){
                    "join-to" -> toPlayers
                    "join-all" -> allPlayers
                    "welcome" -> proxy.players
                    else -> mutableListOf()
                }

                for (player in players) {

                    if(permission && !player.hasPermission("nmsg.receive"))
                        continue

                    val out = msg
                        .replace("%player%", e.player.displayName)
                        .replace("%realname%", e.player.name)
                        .replace("%to-server%", alias(toServer))

                    for(line in out.split("%newline%"))
                        player.msg(line)
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        fun onDisconnection(e: PlayerDisconnectEvent) {

            if(!servers.containsKey(e.player))
                return

            servers.remove(e.player)

            if(silents.contains(e.player.name))
                return

            val fromServer = servers[e.player]!!.name
            val fromPlayers = servers[e.player]!!.players
            val allPlayers = HashSet<ProxiedPlayer>(proxy.players)
            allPlayers.removeAll(fromPlayers)

            listOf("leave-all", "leave-from").forEach{
                var msg = config.getString(it)

                if(it == "leave-from")
                    if (config.getSection("servers").keys.contains(fromServer))
                        msg = config.getSection("servers.$fromServer").getString(it)

                if(msg.isEmpty())
                    return@forEach

                val players = when(it) {
                    "leave-all" -> allPlayers
                    "leave-from" -> fromPlayers
                    else -> mutableListOf()
                }

                for (player in players) {

                    if(permission && !player.hasPermission("nmsg.receive"))
                        continue

                    val out = msg
                            .replace("%player%", e.player.displayName)
                            .replace("%realname%", e.player.name)
                            .replace("%from-server%", alias(fromServer))

                    for(line in out.split("%newline%"))
                        player.msg(line)
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        fun onSwitch(e: ServerSwitchEvent) {

            if(servers.containsKey(e.player))
                return

            servers[e.player] = e.player.server.info

            if(silents.contains(e.player.name))
                return

            val allPlayers = HashSet<ProxiedPlayer>(proxy.players)
            val fromPlayers = servers[e.player]!!.players
            val toPlayers = e.player.server.info.players
            allPlayers.removeAll(fromPlayers)
            allPlayers.removeAll(toPlayers)
            val fromServer = servers[e.player]!!.name
            val toServer = e.player.server.info.name

            listOf("switch-all", "switch-from", "switch-to").forEach {
                var msg = config.getString(it)

                val server = when(it){
                    "switch-to" -> toServer
                    "switch-from" -> fromServer
                    else -> String()
                }

                if(server.isEmpty())
                    if (config.getSection("servers").keys.contains(server))
                        msg = config.getSection("servers.$server").getString(it)

                if(msg.isEmpty())
                    return@forEach

                val players = when(it){
                    "switch-all" -> allPlayers
                    "switch-to" -> toPlayers
                    "switch-from" -> fromPlayers
                    else -> mutableListOf()
                }

                for(player in players){

                    if(permission && !player.hasPermission("nmsg.receive"))
                        continue

                    val out = msg
                            .replace("%player%", e.player.displayName)
                            .replace("%realname%", e.player.name)
                            .replace("%from-server%", alias(fromServer))
                            .replace("%to-server%", alias(toServer))

                    for(line in out.split("%newline%"))
                        player.msg(line)
                }
            }
        }
    }

    fun alias(name: String) = name.also{

        if (!config.getSection("servers").keys.contains(name))
            return@also

        val section = config.getSection("servers.$name")

        if(!section.keys.contains("alias"))
            return@also

        return section.getString("alias")
    }

    val cmd = object: Command("nmsg") {

        override fun execute(sender: CommandSender, args: Array<String>) {

            if(args.isEmpty()) return Unit.also {
                sender.msg("/nmsg silent")
                sender.msg("/nmsg reload")
            }

            val noperm = {sender.msg("§cYou do not have permission")}

            when(args[0].toLowerCase()){
                "silent" -> {
                    if(!sender.hasPermission("nmsg.silent"))
                        return noperm.invoke()

                    val silent = when(silents.contains(sender.name)){
                        true -> false.also { silents.remove(sender.name) }
                        false -> true.also { silents.add(sender.name) }
                    }

                    sender.msg("§cSilent mode is now: $silent")

                    playersConf.set("silent", silents)
                    saveConfig(playersConf, "players.yml")
                }
                "reload" -> {
                    if(!sender.hasPermission("nmsg.reload"))
                        return noperm.invoke()
                    sender.msg("Config reloaded!").also { reload() }
                }
            }

        }
    }
}