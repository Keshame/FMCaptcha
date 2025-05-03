package org.FunMine.FMCaptcha.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final FileConfiguration config;

    private final Map<String, String> capchaPairs;
    private final Set<String> trapWords;
    private final List<PotionEffect> pendingEffects;
    private final List<String> colors;
    private final List<String> unicodes;
    private final List<String> successCommands;

    private final boolean titlesEnabled;
    private final int titleFadeIn;
    private final int titleStay;
    private final int titleFadeOut;
    private final int attempts;
    private final int delaySeconds;
    private final int reminderInterval;
    private final boolean freezeMovement;

    private final Map<String, SoundConfig> soundConfigs = new HashMap<>();

    public static class SoundConfig {
        public final String sound;
        public final float volume;
        public final float pitch;

        public SoundConfig(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        plugin.saveDefaultConfig();

        // Load all configuration values once
        this.capchaPairs = loadCapchaPairs();
        this.trapWords = new HashSet<>(capchaPairs.keySet());
        this.pendingEffects = loadPendingEffects();
        this.colors = config.getStringList("colors").stream()
                .map(this::convertColorFormat)
                .collect(Collectors.toList());
        this.unicodes = config.getStringList("unicodes");
        this.successCommands = config.getStringList("settings.success-commands");

        this.titlesEnabled = config.getBoolean("titles.enabled", true);
        this.titleFadeIn = config.getInt("titles.fade-in", 10);
        this.titleStay = config.getInt("titles.stay", 40);
        this.titleFadeOut = config.getInt("titles.fade-out", 10);

        this.attempts = config.getInt("settings.attempts", 3);
        this.delaySeconds = config.getInt("settings.delay-seconds", 30);
        this.reminderInterval = config.getInt("settings.reminder-interval", 10);

        this.freezeMovement = config.getBoolean("restrictions.freeze-movement", true);

        loadSoundConfigs();
    }

    private void loadSoundConfigs() {
        ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
        if (soundsSection != null) {
            for (String soundName : soundsSection.getKeys(false)) {
                ConfigurationSection soundSection = soundsSection.getConfigurationSection(soundName);
                if (soundSection != null) {
                    soundConfigs.put(soundName, new SoundConfig(
                            soundSection.getString("sound"),
                            (float) soundSection.getDouble("volume", 1.0),
                            (float) soundSection.getDouble("pitch", 1.0)
                    ));
                }
            }
        }
    }

    private Map<String, String> loadCapchaPairs() {
        ConfigurationSection capchaSection = config.getConfigurationSection("capcha");
        return capchaSection.getKeys(false).stream()
                .collect(Collectors.toMap(key -> key, capchaSection::getString));
    }

    private List<PotionEffect> loadPendingEffects() {
        if (!config.getBoolean("effects.apply-while-pending", false)) {
            return Collections.emptyList();
        }

        return config.getMapList("effects.list").stream()
                .map(this::createPotionEffect)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private PotionEffect createPotionEffect(Map<?, ?> rawMap) {
        try {
            Map<String, Object> effectMap = new HashMap<>();
            rawMap.forEach((k, v) -> effectMap.put(k.toString(), v));

            PotionEffectType type = PotionEffectType.getByName(effectMap.get("type").toString());
            if (type == null) return null;

            return new PotionEffect(
                    type,
                    Integer.parseInt(effectMap.getOrDefault("duration", "999999").toString()),
                    Integer.parseInt(effectMap.getOrDefault("amplifier", "0").toString()),
                    Boolean.parseBoolean(effectMap.getOrDefault("ambient", "true").toString()),
                    Boolean.parseBoolean(effectMap.getOrDefault("particles", "false").toString())
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading potion effect: " + e.getMessage());
            return null;
        }
    }

    public Map.Entry<String, String> getRandomCapchaPair() {
        List<Map.Entry<String, String>> entries = new ArrayList<>(capchaPairs.entrySet());
        return entries.get(new Random().nextInt(entries.size()));
    }

    public boolean isTrapWord(String input) {
        return trapWords.contains(input.toLowerCase());
    }

    public List<PotionEffect> getPendingEffects() {
        return Collections.unmodifiableList(pendingEffects);
    }

    public String getRandomColor() {
        return colors.get(new Random().nextInt(colors.size()));
    }

    public String getRandomUnicode() {
        return unicodes.get(new Random().nextInt(unicodes.size()));
    }

    public String getCapchaPromptMessage(String capcha, String color, String unicode) {
        return config.getString("messages.capcha-prompt", "")
                .replace("$capcha", capcha)
                .replace("$color", color)
                .replace("$unicode", unicode)
                .replace('&', '§');
    }

    public String getCapchaReminderMessage(String capcha, String color, String unicode) {
        return config.getString("messages.capcha-reminder", "")
                .replace("$capcha", capcha)
                .replace("$color", color)
                .replace("$unicode", unicode)
                .replace('&', '§');
    }

    public String getCapchaSuccessMessage() {
        return config.getString("messages.capcha-success", "").replace('&', '§');
    }

    public String getCapchaFailMessage() {
        return config.getString("messages.capcha-fail", "").replace('&', '§');
    }

    public String getCapchaKickMessage() {
        return config.getString("messages.capcha-kick", "").replace('&', '§');
    }

    public String getCapchaTitle() {
        return config.getString("messages.capcha-title", "").replace('&', '§');
    }

    public String getCapchaSubtitle() {
        return config.getString("messages.capcha-subtitle", "").replace('&', '§');
    }

    public String getCapchaSuccessTitle() {
        return config.getString("messages.capcha-success-title", "").replace('&', '§');
    }

    public String getCapchaSuccessSubtitle() {
        return config.getString("messages.capcha-success-subtitle", "").replace('&', '§');
    }

    public String getCapchaFailTitle() {
        return config.getString("messages.capcha-fail-title", "").replace('&', '§');
    }

    public String getCapchaFailSubtitle() {
        return config.getString("messages.capcha-fail-subtitle", "").replace('&', '§');
    }

    public List<String> getSuccessCommands() {
        return Collections.unmodifiableList(successCommands);
    }

    public SoundConfig getSoundConfig(String soundName) {
        return soundConfigs.get(soundName);
    }

    public boolean isTitlesEnabled() {
        return titlesEnabled;
    }

    public int getTitleFadeIn() {
        return titleFadeIn;
    }

    public int getTitleStay() {
        return titleStay;
    }

    public int getTitleFadeOut() {
        return titleFadeOut;
    }

    public int getAttempts() { return attempts; }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public int getReminderInterval() {
        return reminderInterval;
    }

    public boolean isFreezeMovement() {
        return freezeMovement;
    }

    private String convertColorFormat(String colorCode) {
        return colorCode.replace('&', '§');
    }
}
