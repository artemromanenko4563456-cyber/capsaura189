package com.example.capsaura;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Mod(modid = "capsaura", name = "CapsAura", version = "1.0.0",
		clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class CapsAura {
	// ---- Настройки (меняйте и пересобирайте) ----
	/** Радиус атаки в блоках. */
	private static final double RANGE = 4.0;
	/** true = бить всех живых мобов (животные, жители), false = только враждебных. */
	private static final boolean ATTACK_PASSIVE = false;
	/** true = бить других игроков. */
	private static final boolean ATTACK_PLAYERS = true;
	/** true = камера сама наводится на цель. */
	private static final boolean AIM = true;
	/** Пауза между ударами в тиках (20 тиков = 1 секунда). 2 = примерно 10 ударов в секунду. */
	private static final int ATTACK_DELAY_TICKS = 2;
	// ---------------------------------------------

	private static KeyBinding toggleKey;
	private static boolean enabled = false;
	private static int cooldown = 0;
	private static File friendsFile;
	private static final Set<String> friends = new HashSet<String>();

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		friendsFile = new File(event.getModConfigurationDirectory(), "capsaura-friends.txt");
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		toggleKey = new KeyBinding("Toggle Kill Aura", Keyboard.KEY_B, "CapsAura");
		ClientRegistry.registerKeyBinding(toggleKey);
		MinecraftForge.EVENT_BUS.register(this);
	}

	/** Читает config/capsaura-friends.txt (создаёт, если нет). */
	private static void loadFriends() {
		friends.clear();
		try {
			if (!friendsFile.exists()) {
				String header = "# Friends: one nickname per line. The aura will not touch them.\n"
						+ "# Lines starting with # are ignored. Case does not matter.\n";
				Files.write(friendsFile.toPath(), header.getBytes(StandardCharsets.UTF_8));
			}
			List<String> lines = Files.readAllLines(friendsFile.toPath(), StandardCharsets.UTF_8);
			for (String line : lines) {
				String name = line.trim();
				if (!name.isEmpty() && !name.startsWith("#")) {
					friends.add(name.toLowerCase(Locale.ROOT));
				}
			}
		} catch (Exception ignored) {
			// не удалось прочитать файл: работаем с пустым списком
		}
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();

		while (toggleKey.isPressed()) {
			enabled = !enabled;
			if (enabled) {
				loadFriends();
			}
			if (mc.thePlayer != null) {
				mc.thePlayer.addChatMessage(new ChatComponentText(
						"Kill Aura: " + (enabled ? "ON (friends: " + friends.size() + ")" : "OFF")));
			}
		}

		if (cooldown > 0) {
			cooldown--;
		}

		if (!enabled || mc.thePlayer == null || mc.theWorld == null
				|| mc.playerController == null || mc.currentScreen != null) {
			return;
		}

		EntityPlayerSP player = mc.thePlayer;
		if (player.isDead || player.getHealth() <= 0) {
			return;
		}

		EntityLivingBase target = null;
		double best = RANGE;

		for (Entity e : mc.theWorld.loadedEntityList) {
			if (!(e instanceof EntityLivingBase) || e == player) {
				continue;
			}
			EntityLivingBase living = (EntityLivingBase) e;
			if (living.isDead || living.getHealth() <= 0 || living instanceof EntityArmorStand) {
				continue;
			}
			if (living instanceof EntityPlayer) {
				if (!ATTACK_PLAYERS) continue;
				if (friends.contains(living.getName().toLowerCase(Locale.ROOT))) continue; // друг
			} else if (!ATTACK_PASSIVE && !(living instanceof IMob)) {
				continue;
			}

			double dist = player.getDistanceToEntity(living);
			if (dist < best) {
				best = dist;
				target = living;
			}
		}

		if (target == null) {
			return;
		}

		// Наведение камеры на центр цели
		if (AIM) {
			Vec3 eyes = player.getPositionEyes(1.0f);
			double dx = target.posX - eyes.xCoord;
			double dy = (target.posY + target.height / 2.0) - eyes.yCoord;
			double dz = target.posZ - eyes.zCoord;
			double flat = Math.sqrt(dx * dx + dz * dz);

			player.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			player.rotationPitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
		}

		// Удар
		if (cooldown <= 0) {
			player.swingItem();
			mc.playerController.attackEntity(player, target);
			cooldown = ATTACK_DELAY_TICKS;
		}
	}
}
