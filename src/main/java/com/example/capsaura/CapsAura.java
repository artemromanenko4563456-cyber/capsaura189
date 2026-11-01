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

	// ======== НАСТРОЙКИ ========
	private static final int TOGGLE_KEY = Keyboard.KEY_B;  // клавиша включения
	private static final double RANGE = 4.0;               // радиус в блоках
	private static final int ATTACKS_PER_SECOND = 7;       // ударов в секунду (от 1 до 20)
	private static final boolean ATTACK_PLAYERS = true;    // бить игроков
	private static final boolean ATTACK_HOSTILE = true;    // бить враждебных мобов
	private static final boolean ATTACK_PASSIVE = false;   // бить животных и жителей
	private static final boolean AIM = true;               // наводить камеру
	// ===========================

	private static KeyBinding toggleKey;
	private static boolean enabled = false;
	private static int attackCharge = 0;
	private static File friendsFile;
	private static final Set<String> friends = new HashSet<String>();

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		friendsFile = new File(event.getModConfigurationDirectory(), "capsaura-friends.txt");
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		toggleKey = new KeyBinding("Toggle Kill Aura", TOGGLE_KEY, "CapsAura");
		ClientRegistry.registerKeyBinding(toggleKey);
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();

		handleToggle(mc);

		if (!enabled || mc.thePlayer == null || mc.theWorld == null
				|| mc.playerController == null || mc.currentScreen != null) {
			return;
		}

		EntityPlayerSP player = mc.thePlayer;
		if (player.isDead || player.getHealth() <= 0) {
			return;
		}

		EntityLivingBase target = findTarget(mc, player);
		if (target == null) {
			return;
		}

		if (AIM) {
			aimAt(player, target);
		}

		// Накопительный счётчик: ровно ATTACKS_PER_SECOND ударов за 20 тиков
		attackCharge += Math.min(ATTACKS_PER_SECOND, 20);
		if (attackCharge >= 20) {
			attackCharge -= 20;
			player.swingItem();
			mc.playerController.attackEntity(player, target);
		}
	}

	private static void handleToggle(Minecraft mc) {
		while (toggleKey.isPressed()) {
			enabled = !enabled;
			attackCharge = 0;
			if (enabled) {
				loadFriends();
			}
			if (mc.thePlayer != null) {
				mc.thePlayer.addChatMessage(new ChatComponentText(
						"Kill Aura: " + (enabled ? "ON (friends: " + friends.size() + ")" : "OFF")));
			}
		}
	}

	/** Ближайшая подходящая цель в радиусе или null. */
	private static EntityLivingBase findTarget(Minecraft mc, EntityPlayerSP player) {
		EntityLivingBase best = null;
		double bestDist = RANGE;

		for (Entity e : mc.theWorld.loadedEntityList) {
			if (!(e instanceof EntityLivingBase)) {
				continue;
			}
			EntityLivingBase living = (EntityLivingBase) e;
			if (!isValidTarget(player, living)) {
				continue;
			}
			double dist = player.getDistanceToEntity(living);
			if (dist < bestDist) {
				bestDist = dist;
				best = living;
			}
		}
		return best;
	}

	private static boolean isValidTarget(EntityPlayerSP self, EntityLivingBase e) {
		if (e == self || e.isDead || e.getHealth() <= 0 || e instanceof EntityArmorStand) {
			return false;
		}
		if (e instanceof EntityPlayer) {
			if (!ATTACK_PLAYERS) return false;
			return !friends.contains(e.getName().toLowerCase(Locale.ROOT));
		}
		if (e instanceof IMob) {
			return ATTACK_HOSTILE;
		}
		return ATTACK_PASSIVE;
	}

	/** Поворачивает камеру на центр цели. */
	private static void aimAt(EntityPlayerSP player, EntityLivingBase target) {
		Vec3 eyes = player.getPositionEyes(1.0f);
		double dx = target.posX - eyes.xCoord;
		double dy = (target.posY + target.height / 2.0) - eyes.yCoord;
		double dz = target.posZ - eyes.zCoord;
		double flat = Math.sqrt(dx * dx + dz * dz);

		player.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		player.rotationPitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
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
			// файл не прочитался: работаем с пустым списком
		}
	}
}
