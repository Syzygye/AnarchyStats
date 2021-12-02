/*
 * This file is part of AnarchyStats.
 *
 * AnarchyStats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AnarchyStats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AnarchyStats.  If not, see <https://www.gnu.org/licenses/>.
 */

package lol.hyper.anarchystats;

import lol.hyper.anarchystats.commands.CommandInfo;
import lol.hyper.anarchystats.commands.CommandReload;
import lol.hyper.anarchystats.tools.AbstractCommand;
import lol.hyper.anarchystats.tools.MessageParser;
import lol.hyper.anarchystats.tools.WorldSize;
import lol.hyper.githubreleaseapi.GitHubRelease;
import lol.hyper.githubreleaseapi.GitHubReleaseAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Logger;

public final class AnarchyStats extends JavaPlugin {

    public static String worldSize;
    public final File configFile = new File(this.getDataFolder(), "config.yml");
    public final Logger logger = this.getLogger();
    public final ArrayList<Path> worldPaths = new ArrayList<>();
    public final int CONFIG_VERSION = 1;
    public FileConfiguration config;
    public CommandReload commandReload;
    public MessageParser messageParser;

    @Override
    public void onEnable() {
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
        Bukkit.getScheduler().runTaskAsynchronously(this, this::updateWorldSize);

        new Metrics(this, 6877);

        Bukkit.getScheduler().runTaskAsynchronously(this, this::checkForUpdates);
    }

    public void updateWorldSize() {
        worldSize = WorldSize.readableFileSize(WorldSize.getWorldSize(worldPaths));
    }

    public void loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        if (worldPaths.size() > 0) {
            worldPaths.clear();
        }
        for (String x : config.getStringList("worlds-to-use")) {
            Path currentPath =
                    Paths.get(Paths.get(".").toAbsolutePath().normalize().toString() + File.separator + x);
            if (!currentPath.toFile().exists()) {
                logger.warning("World folder \"" + x + "\" does not exist! Excluding from size calculation.");
            } else {
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
}
