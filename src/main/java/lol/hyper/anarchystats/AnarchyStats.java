package lol.hyper.anarchystats;

import lol.hyper.anarchystats.commands.CommandInfo;
import lol.hyper.anarchystats.commands.CommandReload;
import lol.hyper.anarchystats.tools.AbstractCommand;
import lol.hyper.anarchystats.tools.MessageParser;
import lol.hyper.anarchystats.tools.WorldSize;
import lol.hyper.githubreleaseapi.GitHubRelease;
import lol.hyper.githubreleaseapi.GitHubReleaseAPI;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import space.arim.morepaperlib.MorePaperLib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class AnarchyStats extends JavaPlugin {

    public String worldSize;
    public final File configFile = new File(this.getDataFolder(), "config.yml");
    public final Logger logger = this.getLogger();
    public final List<Path> worldPaths = new ArrayList<>();
    public final int CONFIG_VERSION = 2;
    public final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BukkitAudiences adventure;
    public FileConfiguration config;
    public CommandReload commandReload;
    public MessageParser messageParser;
    public MorePaperLib morePaperLib;

    private boolean checkUpdatesEnabled; // Nueva variable para habilitar/deshabilitar la comprobación de actualizaciones

    @Override
    public void onEnable() {
        this.adventure = BukkitAudiences.create(this);
        morePaperLib = new MorePaperLib(this);
        messageParser = new MessageParser(this);
        commandReload = new CommandReload(this);

        if (!configFile.exists()) {
            this.saveResource("config.yml", true);
            logger.info("Copying default config!");
        }

        loadConfig();

        AbstractCommand infoCommand = new CommandInfo(config.getString("info-command-override"), this);
        infoCommand.register();

        this.getCommand("anarchystats").setExecutor(commandReload);
        morePaperLib.scheduling().asyncScheduler().run(this::updateWorldSize);

        // Se eliminó la inicialización de bStats (Metrics)
        // new Metrics(this, 6877);

        // Solo ejecutar la comprobación de actualizaciones si está habilitado en la configuración
        if (checkUpdatesEnabled) {
            morePaperLib.scheduling().asyncScheduler().run(this::checkForUpdates);
        }
    }

    public void updateWorldSize() {
        worldSize = WorldSize.readableFileSize(WorldSize.getWorldSize(worldPaths));
    }

    public void loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);

        // Leer la configuración de la opción de verificación de actualizaciones
        checkUpdatesEnabled = config.getBoolean("check-updates-enabled", true);

        if (worldPaths.size() > 0) {
            worldPaths.clear();
        }

        for (String worldFolder : config.getStringList("worlds-to-use")) {
            Path currentPath = Paths.get(Paths.get(".").toAbsolutePath().normalize() + File.separator + worldFolder);
            if (!currentPath.toFile().exists()) {
                logger.warning("World folder \"" + worldFolder + "\" does not exist! Excluding from size calculation.");
            } else {
                logger.info("Adding " + worldFolder);
                worldPaths.add(currentPath);
            }
        }

        if (config.getInt("config-version") != CONFIG_VERSION) {
            logger.warning("You configuration is out of date! Some features may not work!");
        }
    }

    public void checkForUpdates() {
        GitHubReleaseAPI api;
        try {
            api = new GitHubReleaseAPI("AnarchyStats", "hyperdefined");
        } catch (IOException e) {
            logger.warning("Unable to check updates!");
            e.printStackTrace();
            return;
        }
        GitHubRelease current = api.getReleaseByTag(this.getDescription().getVersion());
        GitHubRelease latest = api.getLatestVersion();
        if (current == null) {
            logger.warning("You are running a version that does not exist on GitHub. If you are in a dev environment, you can ignore this. Otherwise, this is a bug!");
            return;
        }
        int buildsBehind = api.getBuildsBehind(current);
        if (buildsBehind == 0) {
            logger.info("You are running the latest version.");
        } else {
            logger.warning("A new version is available (" + latest.getTagVersion() + ")! You are running version " + current.getTagVersion() + ". You are " + buildsBehind + " version(s) behind.");
        }
    }

    public BukkitAudiences getAdventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }
}
