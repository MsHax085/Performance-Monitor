/*
    * PerformanceMonitor Bukkit Plugin, monitoring of server performance.
    * Copyright (C) 2012 Richard Dahlgren
    * 
    * This program is free software; you can redistribute it and/or modify
    * it under the terms of the GNU General Public License as published by
    * the Free Software Foundation; either version 3 of the License, or
    * (at your option) any later version.
    * 
    * This program is distributed in the hope that it will be useful,
    * but WITHOUT ANY WARRANTY; without even the implied warranty of
    * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    * GNU General Public License for more details.
    * 
    * You should have received a copy of the GNU General Public License
    * along with this program; if not, write to the Free Software Foundation,
    * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 */

package me.mshax085.performanceMonitor;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;
import me.mshax085.performanceMonitor.disk.DiskFileSize;
import me.mshax085.performanceMonitor.listeners.CommandListener;
import me.mshax085.performanceMonitor.listeners.LoginListener;
import me.mshax085.performanceMonitor.memory.MemoryMeter;
import me.mshax085.performanceMonitor.messages.Broadcast;
import me.mshax085.performanceMonitor.restart.RestartCounter;
import me.mshax085.performanceMonitor.tps.TpsMeter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.MetricsLite;

/*
 * Monitor Class
 * 
 * @package     me.mshax085.performanceMonitor
 * @category    StartUp - Organizer
 * @author      Richard Dahlgren (MsHax085)
 */
public class Monitor extends JavaPlugin implements Runnable {
    private static final Logger log = Logger.getLogger("Minecraft");
    private final Configuration configClass = new Configuration();
    private LoginListener loginListener;
    private RestartCounter restartCounter;
    private DiskFileSize diskFileSize;
    private Broadcast broadcast;
    private MemoryMeter memoryMeter;
    private TpsMeter tpsMeter;
    private FileConfiguration config = null;
    private File configFile = null;
    private boolean latestVersion = true;
    public int uniqueLogins = 0;
    
    // -------------------------------------------------------------------------
    
    /*
     * GetBroadcaster
     * 
     * Return Broadcaster Class Refference
     * 
     * @access  public
     * @return  Broadcaster
     */
    public final Broadcast getBroadcaster() {
        if (this.broadcast == null) {
            this.broadcast = new Broadcast(this);
        }
        return this.broadcast;
    }

    /*
     * GetConfig
     * 
     * Return FileConfiguration Refference
     * 
     * @access  public
     * @return  FileConfiguration
     */
    @Override
    public final FileConfiguration getConfig() {
        if (this.config == null) {
            this.reloadConfigFile();
        }
        return this.config;
    }
    
    /*
     * GetConfigurationClass
     * 
     * Return Configuration Class Refference
     * 
     * @access  public
     * @return  Configuration
     */
    public final Configuration getConfigurationClass() {
        return this.configClass;
    }
    
    /*
     * GetDiskFileSize
     * 
     * Return DiskFileSize Class Refference
     * 
     * @access  public
     * @return  DiskFileSize
     */
    public final DiskFileSize getDiskFileSize() {
        if (this.diskFileSize == null) {
            this.diskFileSize = new DiskFileSize(this);
        }
        return this.diskFileSize;
    }
    
    /*
     * GetMemoryMeter
     * 
     * Return MemoryMeter Class Refference
     * 
     * @access  public
     * @return  MemoryMeter
     */
    public final MemoryMeter getMemoryMeter() {
        if (this.memoryMeter == null) {
            this.memoryMeter = new MemoryMeter();
        }
        return this.memoryMeter;
    }

    /*
     * GetRestartCounter
     * 
     * Return RestartCounter Class Refference
     * 
     * @access  public
     * @return  RestartCounter
     */
    public final RestartCounter getRestartCounter() {
        return this.restartCounter;
    }
    
    /*
     * GetTpsMeter
     * 
     * Return TpsMeter Class Refference
     * 
     * @access  public
     * @return  TpsMeter
     */
    public final TpsMeter getTpsMeter() {
        return this.tpsMeter;
    }
    
    /*
     * GetVersion
     * 
     * Return Plugin Version
     * 
     * @access  public
     * @return  String
     */
    public final String getVersion() {
        return this.getDescription().getVersion();
    }
    
    /*
     * IsLatestVersion
     * 
     * Returns true if plugin is of latest version
     * 
     * @access  public
     * @return  boolean
     */
    public final boolean isLatestVersion() {
        return this.latestVersion;
    }
    
    /*
     * LogMsg
     * 
     * Send Message To Console
     * 
     * @access  private
     * @param   String
     */
    private void logMsg(final String msg) {
        final String logMsg = "[PerformanceMonitor " + this.getVersion() + "] " + msg;
        Monitor.log.info(logMsg);
    }

    /*
     * OnEnable
     * 
     * Called on plugin start
     * 
     * @access  public
     */
    @Override
    public final void onEnable() {
        this.getConfigurationClass().update(this);
        this.validateListeners();
        
        final CommandListener cl = new CommandListener(this);
        this.getCommand("ss").setExecutor(cl);
        this.getCommand("serverstate").setExecutor(cl);

        this.logMsg("Plugin enabled!");

        if (this.getConfigurationClass().checkForUpdatesOnStart) {
            new Thread(this).start();
        }
        try {
        MetricsLite metrics = new MetricsLite(this);
        metrics.start();
        } catch (IOException e) {
            // Catch Exception
        }
    }

    /*
     * OnDisable
     * 
     * Called on plugin disable
     * 
     * @access  public
     */
    @Override
    public final void onDisable() {
        if (this.getConfigurationClass().showTps) {
            this.registerSchedulingTasks(true);
        }
        this.logMsg("Plugin disabled!");
    }

    /*
     * RegisterSchedulingTasks
     * 
     * Start or cancel scheduling tasks
     * 
     * @access  private
     * @param   boolean
     */
    private void registerSchedulingTasks(final boolean cancel) {
        if (!cancel) {
            this.logMsg("Starting tps meter ...");
            this.getServer().getScheduler().scheduleSyncRepeatingTask(this, this.tpsMeter, 0L, 40L);
        } else {
            this.getServer().getScheduler().cancelTasks(this);
        }
    }

    /*
     * ReloadConfigFile
     * 
     * Refresh Stats Based On Configuration
     * 
     * @access  public
     */
    public final void reloadConfigFile() {
        if (this.configFile == null) {
            this.configFile = new File(this.getDataFolder(), "config.yml");
        }
        this.config = YamlConfiguration.loadConfiguration(this.configFile);

        final InputStream defaultConfigStream = this.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigStream);
            this.config.setDefaults(defaultConfig);
        }
        this.getConfigurationClass().update(this);
        this.validateListeners();
    }
    
    /*
     * Run
     * 
     * Runnable Thread Used To Check If Plugin Is Of Latest Version
     * 
     * @access  public
     */
    @Override
    public final void run() {
        final String address = "http://consiliumcraft.com/plugins/pmon.html";
        try {
            final URL url = new URL(address);
            final URLConnection connection = url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-agent", "PerformanceMonitor " + this.getVersion());
            
            final InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());
            final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            final String version = bufferedReader.readLine();
            if (version != null && !version.equals(this.getVersion())) {
                this.latestVersion = false;
                this.logMsg("There is a new version available for download!");
            }
            
            connection.getInputStream().close();
        } catch (IOException ex) {
            this.logMsg("Could not check for latest version: " + ex.getMessage());
        }
    }
    
    /*
     * SaveConfigFile
     * 
     * Save Stats Based on Configuration
     * 
     * @access  public
     */
    public final void saveConfigFile() {
        if (this.config != null && this.configFile != null) {
            try {
                this.getConfig().save(this.configFile);
            } catch (IOException ex) {
                this.logMsg("Could not save config to " + this.configFile + "! " + ex.getMessage());
            }
        }
    }
    
    /*
     * ValidateListeners
     * 
     * Enable listeners if disabled and enabled in config
     * 
     * @access  private
     * @return  boolean
     */
    private boolean validateListeners() {
        if (this.getConfigurationClass().showLastRestart) {
            if (this.restartCounter == null) {
                this.restartCounter = new RestartCounter();
                this.restartCounter.setStartTime();
            }
        }
        
        if (this.getConfigurationClass().showTps) {
            if (this.tpsMeter == null) {
                this.tpsMeter = new TpsMeter(this);
                this.registerSchedulingTasks(false);
            }
        }
        
        if (this.getConfigurationClass().statusMessageUponLogin || this.getConfigurationClass().showUniquePlayerLogins) {
            if (this.loginListener == null) {
                this.loginListener = new LoginListener(this);
                this.getServer().getPluginManager().registerEvents(this.loginListener, this);
            }
        }
        return true;
    }
}