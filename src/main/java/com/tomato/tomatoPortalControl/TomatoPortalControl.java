package com.tomato.tomatoPortalControl;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import java.io.*;
import java.util.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

public class TomatoPortalControl extends JavaPlugin implements Listener {

    private boolean debugMode = false;
    private Set<Material> portalBlocks = new HashSet<>();
    private Set<EntityType> whitelistedEntities = new HashSet<>();
    private Map<UUID, Long> cooldowns = new HashMap<>();
    private long cooldownTime = 3000; // 3秒冷却时间

    @Override
    public void onEnable() {
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 加载配置
        saveDefaultConfig();
        loadConfig();

        // 注册命令
        Objects.requireNonNull(getCommand("portalcontrol")).setExecutor(this);

        getLogger().info(ChatColor.GREEN + "传送门控制插件已启用！");
        getLogger().info(ChatColor.YELLOW + "禁止非玩家实体穿越下界门和末地门（折跃门除外）");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "传送门控制插件已禁用");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        // 加载调试模式
        debugMode = config.getBoolean("debug-mode", false);

        // 加载传送门方块
        portalBlocks.clear();
        for (String block : config.getStringList("portal-blocks")) {
            try {
                Material material = Material.valueOf(block.toUpperCase());
                portalBlocks.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("无效的方块类型: " + block);
            }
        }

        // 如果配置为空，添加默认值
        if (portalBlocks.isEmpty()) {
            portalBlocks.add(Material.NETHER_PORTAL);
            portalBlocks.add(Material.END_PORTAL);
            config.set("portal-blocks", Arrays.asList("NETHER_PORTAL", "END_PORTAL"));
        }

        // 加载白名单实体
        whitelistedEntities.clear();
        for (String entity : config.getStringList("whitelisted-entities")) {
            try {
                EntityType entityType = EntityType.valueOf(entity.toUpperCase());
                whitelistedEntities.add(entityType);
            } catch (IllegalArgumentException e) {
                getLogger().warning("无效的实体类型: " + entity);
            }
        }

        // 保存配置
        saveConfig();

        if (debugMode) {
            getLogger().info("已加载配置:");
            getLogger().info("传送门方块: " + portalBlocks);
            getLogger().info("白名单实体: " + whitelistedEntities);
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        handlePortalEvent(event.getEntity(), event);
    }

    private void handlePortalEvent(Entity entity, Event event) {
        // 如果是玩家，直接允许
        if (entity instanceof Player) {
            // 检查冷却时间
            if (System.currentTimeMillis() - cooldowns.getOrDefault(entity.getUniqueId(), 0L) < cooldownTime) {
                if (debugMode) {
                    getLogger().info("玩家 " + entity.getName() + " 在冷却时间内使用传送门");
                }
                return;
            }
            cooldowns.put(entity.getUniqueId(), System.currentTimeMillis());
            return;
        }

        // 检查是否在白名单中
        if (whitelistedEntities.contains(entity.getType())) {
            if (debugMode) {
                getLogger().info("实体 " + entity.getType() + " 在白名单中，允许穿越");
            }
            return;
        }

        // 检查是否在末地折跃门
        if (entity.getWorld().getEnvironment() == World.Environment.THE_END) {
            Material blockType = entity.getLocation().getBlock().getType();
            if (blockType == Material.END_GATEWAY) {
                if (debugMode) {
                    getLogger().info("实体 " + entity.getType() + " 通过折跃门，允许穿越");
                }
                return;
            }
        }

        // 取消传送
        if (event instanceof EntityPortalEvent) {
            ((EntityPortalEvent) event).setCancelled(true);
        }

        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.damage(0.5); // 轻微伤害表示被阻挡
        }

        if (entity instanceof Projectile) {
            entity.remove();
        }

        if (debugMode) {
            getLogger().info("阻止 " + entity.getType() + " 穿越传送门");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Material blockType = event.getClickedBlock().getType();

        if (portalBlocks.contains(blockType)) {
            // 显示传送门信息
            if (player.isSneaking() && player.hasPermission("portalcontrol.info")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.GOLD + "[" + ChatColor.YELLOW + "传送门信息" + ChatColor.GOLD + "]");
                player.sendMessage(ChatColor.GRAY + "类型: " + ChatColor.AQUA + getPortalName(blockType));
                player.sendMessage(ChatColor.GRAY + "状态: " + ChatColor.GREEN + "已启用保护");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            }
        }
    }

    private String getPortalName(Material material) {
        switch (material) {
            case NETHER_PORTAL: return "下界传送门";
            case END_PORTAL: return "末地传送门";
            case END_GATEWAY: return "末地折跃门";
            default: return material.toString();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("portalcontrol")) {
            if (args.length == 0) {
                showHelp(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    if (sender.hasPermission("portalcontrol.reload")) {
                        reloadConfig();
                        loadConfig();
                        sender.sendMessage(ChatColor.GREEN + "配置已重新加载！");
                    } else {
                        sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
                    }
                    return true;

                case "debug":
                    if (sender.hasPermission("portalcontrol.debug")) {
                        debugMode = !debugMode;
                        getConfig().set("debug-mode", debugMode);
                        saveConfig();
                        sender.sendMessage(ChatColor.YELLOW + "调试模式: " +
                                (debugMode ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));
                    } else {
                        sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
                    }
                    return true;

                case "menu":
                    if (sender instanceof Player && sender.hasPermission("portalcontrol.menu")) {
                        openConfigMenu((Player) sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
                    }
                    return true;

                default:
                    showHelp(sender);
                    return true;
            }
        }
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== " + ChatColor.YELLOW + "传送门控制插件帮助" + ChatColor.GOLD + " =====");
        sender.sendMessage(ChatColor.GOLD + "/portalcontrol reload " + ChatColor.GRAY + "- 重新加载配置");
        if (sender.hasPermission("portalcontrol.debug")) {
            sender.sendMessage(ChatColor.GOLD + "/portalcontrol debug " + ChatColor.GRAY + "- 切换调试模式");
        }
        if (sender instanceof Player && sender.hasPermission("portalcontrol.menu")) {
            sender.sendMessage(ChatColor.GOLD + "/portalcontrol menu " + ChatColor.GRAY + "- 打开配置菜单");
        }
        sender.sendMessage(ChatColor.GOLD + "==================================");
    }

    private void openConfigMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "传送门控制设置");

        // 调试模式按钮
        ItemStack debugItem = new ItemStack(debugMode ? Material.REDSTONE_TORCH : Material.LEVER);
        ItemMeta debugMeta = debugItem.getItemMeta();
        debugMeta.setDisplayName(ChatColor.YELLOW + "调试模式");
        debugMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "当前状态: " + (debugMode ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"),
                "",
                ChatColor.GOLD + "点击切换"
        ));
        debugItem.setItemMeta(debugMeta);
        menu.setItem(11, debugItem);

        // 传送门类型按钮
        ItemStack portalsItem = new ItemStack(Material.OBSIDIAN);
        ItemMeta portalsMeta = portalsItem.getItemMeta();
        portalsMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "受保护的传送门");
        List<String> portalsLore = new ArrayList<>();
        portalsLore.add(ChatColor.GRAY + "当前受保护的传送门:");
        for (Material portal : portalBlocks) {
            portalsLore.add(ChatColor.DARK_PURPLE + " - " + getPortalName(portal));
        }
        portalsLore.add("");
        portalsLore.add(ChatColor.GOLD + "配置文件中修改");
        portalsMeta.setLore(portalsLore);
        portalsItem.setItemMeta(portalsMeta);
        menu.setItem(13, portalsItem);

        // 白名单实体按钮
        ItemStack whitelistItem = new ItemStack(Material.NAME_TAG);
        ItemMeta whitelistMeta = whitelistItem.getItemMeta();
        whitelistMeta.setDisplayName(ChatColor.GREEN + "白名单实体");
        List<String> whitelistLore = new ArrayList<>();
        whitelistLore.add(ChatColor.GRAY + "当前白名单实体:");
        if (whitelistedEntities.isEmpty()) {
            whitelistLore.add(ChatColor.RED + "无");
        } else {
            for (EntityType entity : whitelistedEntities) {
                whitelistLore.add(ChatColor.GREEN + " - " + entity.name());
            }
        }
        whitelistLore.add("");
        whitelistLore.add(ChatColor.GOLD + "配置文件中修改");
        whitelistMeta.setLore(whitelistLore);
        whitelistItem.setItemMeta(whitelistMeta);
        menu.setItem(15, whitelistItem);

        // 关闭按钮
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "关闭菜单");
        closeItem.setItemMeta(closeMeta);
        menu.setItem(26, closeItem);

        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "传送门控制设置")) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

            if (event.getRawSlot() == 11) { // 调试模式按钮
                debugMode = !debugMode;
                getConfig().set("debug-mode", debugMode);
                saveConfig();
                player.sendMessage(ChatColor.YELLOW + "调试模式: " +
                        (debugMode ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openConfigMenu(player);
            } else if (event.getRawSlot() == 26) { // 关闭按钮
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
            }
        }
    }
}