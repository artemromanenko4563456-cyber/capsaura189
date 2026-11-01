package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class CapsAuraClient implements ClientModInitializer {

	// ======== НАСТРОЙКИ ========
	private static final int TOGGLE_KEY = GLFW.GLFW_KEY_B; // клавиша включения
	private static final double RANGE = 4.0;               // радиус в блоках
	private static final int ATTACKS_PER_SECOND = 7;       // ударов в секунду (от 1 до 20)
	private static final boolean ATTACK_PLAYERS = true;    // бить игроков
	private static final boolean ATTACK_HOSTILE = true;    // бить враждебных мобов
	private static final boolean ATTACK_PASSIVE = false;   // бить животных и жителей
	private static final boolean AIM = true;               // наводить камеру
	// ===========================

	private static KeyMapping toggleKey;
	private static boolean enabled = false;
	private static int attackCharge = 0;
	private static final Set<String> friends = new HashSet<>();

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("capsaura", "main"));

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.toggle", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));

		ClientTickEvents.END_CLIENT_TICK.register(CapsAuraClient::onTick);
	}

	private static void onTick(Minecraft mc) {
		handleToggle(mc);

		if (!enabled || mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		LocalPlayer player = mc.player;
		if (!player.isAlive()) {
			return;
		}

		LivingEntity target = findTarget(mc, player);
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
			mc.gameMode.attack(player, target);
			player.swing(InteractionHand.MAIN_HAND);
		}
	}

	private static void handleToggle(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			attackCharge = 0;
			if (enabled) {
				loadFriends();
			}
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal(
						"Kill Aura: " + (enabled ? "ON (friends: " + friends.size() + ")" : "OFF")));
			}
		}
	}

	/** Ближайшая подходящая цель в радиусе или null. */
	private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
		LivingEntity best = null;
		double bestDist = RANGE * RANGE;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !isValidTarget(player, living)) {
				continue;
			}
			double dist = player.distanceToSqr(living);
			if (dist < bestDist) {
				bestDist = dist;
				best = living;
			}
		}
		return best;
	}

	private static boolean isValidTarget(LocalPlayer self, LivingEntity e) {
		if (e == self || !e.isAlive() || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player) {
			if (!ATTACK_PLAYERS) return false;
			return !friends.contains(e.getName().getString().toLowerCase(Locale.ROOT));
		}
		if (e instanceof Enemy) {
			return ATTACK_HOSTILE;
		}
		return ATTACK_PASSIVE;
	}

	/** Поворачивает камеру на центр цели. */
	private static void aimAt(LocalPlayer player, LivingEntity target) {
		Vec3 from = player.getEyePosition();
		Vec3 to = target.getBoundingBox().getCenter();
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double flat = Math.sqrt(dx * dx + dz * dz);

		player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
		player.setXRot((float) (-Math.toDegrees(Math.atan2(dy, flat))));
	}

	/** Читает config/capsaura-friends.txt (создаёт, если нет). */
	private static void loadFriends() {
		friends.clear();
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("capsaura-friends.txt");
			if (!Files.exists(file)) {
				Files.writeString(file,
						"# Friends: one nickname per line. The aura will not touch them.\n"
						+ "# Lines starting with # are ignored. Case does not matter.\n");
			}
			for (String line : Files.readAllLines(file)) {
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
