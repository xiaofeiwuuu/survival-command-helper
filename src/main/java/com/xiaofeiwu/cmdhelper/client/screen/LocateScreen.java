package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.ChatResultCapture;
import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.CommandSuggestionQuery;
import com.xiaofeiwu.cmdhelper.client.locate.LocateLabeler;
import com.xiaofeiwu.cmdhelper.client.locate.LocateNames;
import com.xiaofeiwu.cmdhelper.client.locate.LocateResult;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryDataSource;
import com.xiaofeiwu.cmdhelper.client.teleport.SafeTeleport;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** /locate structure|biome|poi — its result only ever shows up as one line of chat text, which
 *  vanilla doesn't make easy to copy, so this captures that line (see ChatResultCapture) and
 *  shows it with its own copy button instead. */
public class LocateScreen extends CmdHelperScreen {

    private enum LocateType {STRUCTURE, BIOME, POI}

    private static final Map<LocateType, String> TYPE_LABELS = Map.of(
            LocateType.STRUCTURE, "结构（要塞、村庄、下界要塞...）",
            LocateType.BIOME, "生物群系",
            LocateType.POI, "兴趣点（村民工作方块等）"
    );

    private static final Map<LocateType, String> COMMAND_KEYWORD = Map.of(
            LocateType.STRUCTURE, "structure",
            LocateType.BIOME, "biome",
            LocateType.POI, "poi"
    );

    // Minecraft only syncs a short allowlist of registries to the client (biome, chat_type,
    // trim_pattern/material, dimension_type, damage_type — see RegistrySynchronization
    // .NETWORKABLE_REGISTRIES). worldgen/structure and point_of_interest_type are NOT on that
    // list, so level.registryAccess().registryOrThrow(...) throws for them on the client (used
    // to crash this screen instantly). Instead, structure/POI ids are fetched from the server
    // the same way chat's Tab-completion does (CommandSuggestionQuery) — that's authoritative
    // and mod-inclusive. These vanilla lists are just the instant fallback shown before that
    // network round trip resolves (or if it somehow comes back empty).
    private static final List<String> VANILLA_STRUCTURES = List.of(
            "minecraft:ancient_city", "minecraft:bastion_remnant", "minecraft:buried_treasure",
            "minecraft:desert_pyramid", "minecraft:end_city", "minecraft:fortress", "minecraft:igloo",
            "minecraft:jungle_pyramid", "minecraft:mansion", "minecraft:mineshaft", "minecraft:mineshaft_mesa",
            "minecraft:monument", "minecraft:nether_fossil", "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm",
            "minecraft:pillager_outpost", "minecraft:ruined_portal", "minecraft:ruined_portal_desert",
            "minecraft:ruined_portal_jungle", "minecraft:ruined_portal_mountain", "minecraft:ruined_portal_nether",
            "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_swamp", "minecraft:shipwreck",
            "minecraft:shipwreck_beached", "minecraft:stronghold", "minecraft:swamp_hut", "minecraft:trail_ruins",
            "minecraft:village_desert", "minecraft:village_plains", "minecraft:village_savanna",
            "minecraft:village_snowy", "minecraft:village_taiga"
    );

    private static final List<String> VANILLA_POI_TYPES = List.of(
            "minecraft:armorer", "minecraft:butcher", "minecraft:cartographer", "minecraft:cleric",
            "minecraft:farmer", "minecraft:fisherman", "minecraft:fletcher", "minecraft:home",
            "minecraft:leatherworker", "minecraft:librarian", "minecraft:lodestone", "minecraft:mason",
            "minecraft:meeting", "minecraft:nether_portal", "minecraft:shepherd", "minecraft:toolsmith",
            "minecraft:weaponsmith"
    );

    private LocateType type;
    private String targetId;
    private List<String> serverIds;
    // The replies to /locate, by translation key: found (…success) and not found. Matching on the
    // key instead of "the next chat line" means an unrelated message can't be mistaken for the answer.
    private static final Set<String> LOCATE_REPLY_KEYS = Set.of(
            "commands.locate.structure.success", "commands.locate.biome.success", "commands.locate.poi.success",
            "commands.locate.structure.not_found", "commands.locate.biome.not_found", "commands.locate.poi.not_found",
            "commands.locate.structure.invalid");

    private String resultText = "";
    private EditBox resultBox;
    private LocateResult.Position resultPosition;
    private Button teleportButton;
    private EditBox nameBox;
    private LocateLabeler labeler;
    private int resultLabelY;

    public LocateScreen(Screen parent) {
        this(parent, LocateType.STRUCTURE, null, null);
    }

    private LocateScreen(Screen parent, LocateType type, String targetId, List<String> serverIds) {
        super(Component.literal("查找坐标 /locate"), parent);
        this.type = type;
        this.targetId = targetId;
        this.serverIds = serverIds;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 300, 20,
                List.of(LocateType.values()), type, TYPE_LABELS::get,
                value -> this.minecraft.setScreen(new LocateScreen(parent, value, null, null)))));

        if (type != LocateType.BIOME && serverIds == null) {
            fetchServerIds(type);
        }

        List<String> ids = idsFor(type);
        if (targetId == null || !ids.contains(targetId)) {
            targetId = ids.isEmpty() ? null : ids.get(0);
        }
        this.labeler = new LocateLabeler(LocateNames.shared()::get, RegistryDataSource::modNameOf,
                key -> I18n.exists(key) ? I18n.get(key) : null);
        Map<String, String> labels = labelsFor(type, ids);

        int y = 92;
        if (!ids.isEmpty()) {
            this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 66, 300, 20,
                    ids, targetId, id -> labels.getOrDefault(id, id),
                    value -> {
                        this.targetId = value;
                        updateNameBox();
                    })));

            // Structures and points of interest (especially modded ones) usually have no Chinese
            // name anywhere in the game, so let the player give one; it's remembered and used
            // in the list from then on.
            this.nameBox = new EditBox(this.font, centerX - 150, y, 190, 18, Component.literal("自定义中文名"));
            tip(this.nameBox, "给当前选中的目标起个中文名并保存\n保存后下拉里就显示这个名字；\n清空后再保存可还原。");
            this.nameBox.setMaxLength(LocateNames.MAX_NAME_LENGTH);
            updateNameBox();
            this.addRenderableWidget(this.nameBox);
            this.addRenderableWidget(Button.builder(Component.literal("保存名称"), b -> saveName())
                    .bounds(centerX + 46, y, 104, 18).build());
            y += 26;
        }

        int buttonsY = y;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b -> runLocate())
                .bounds(centerX + 10, buttonsY, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.copyToClipboard(cmd);
            }
        }).bounds(centerX + 105, buttonsY, 90, 18).build());

        this.resultLabelY = buttonsY + 26;
        this.resultBox = new EditBox(this.font, centerX - 150, resultLabelY + 10, 300, 18, Component.literal("结果"));
        tip(this.resultBox, "服务器返回的原文（只读）\n可点「复制结果」复制；找到后可点「传送过去」。");
        this.resultBox.setValue(resultText);
        this.resultBox.setEditable(false);
        this.addRenderableWidget(this.resultBox);

        this.addRenderableWidget(Button.builder(Component.literal("复制结果"), b -> {
            if (!resultText.isEmpty()) {
                CommandExecutor.copyRaw(resultText);
            }
        }).bounds(centerX - 150, resultLabelY + 34, 90, 18).build());

        this.teleportButton = this.addRenderableWidget(Button.builder(Component.literal("传送过去"), b -> teleportToResult())
                .bounds(centerX - 54, resultLabelY + 34, 100, 18).build());
    }

    private void teleportToResult() {
        LocateResult.Position target = resultPosition;
        if (target == null) {
            return;
        }
        if (target.y() != null) {
            CommandExecutor.execute(CommandBuilders.teleportToHeight(target.x(), target.y(), target.z()));
        } else {
            // A structure's height is unknown ("~"): SafeTeleport finds the ground, loading the
            // area first if it's far away and not loaded yet.
            SafeTeleport.toColumn(target.x(), target.z());
        }
        this.minecraft.setScreen(null);
    }

    private LocateLabeler.Kind kind() {
        return switch (type) {
            case STRUCTURE -> LocateLabeler.Kind.STRUCTURE;
            case BIOME -> LocateLabeler.Kind.BIOME;
            case POI -> LocateLabeler.Kind.POI;
        };
    }

    /** Shows the saved custom name (if any) for the selected target, and says whether it has one at all. */
    private void updateNameBox() {
        if (nameBox == null || targetId == null) {
            return;
        }
        String saved = LocateNames.shared().get(targetId);
        nameBox.setValue(saved == null ? "" : saved);
        nameBox.setHint(Component.literal(labeler.nameOf(kind(), targetId) != null
                ? "输入可改名，清空后保存可还原"
                : "还没有中文名，在这里起一个"));
    }

    private void saveName() {
        if (targetId == null) {
            return;
        }
        LocateNames.shared().set(targetId, nameBox.getValue());
        // Rebuild so the dropdown's labels pick the new name up.
        this.minecraft.setScreen(new LocateScreen(parent, type, targetId, serverIds));
    }

    private void runLocate() {
        String cmd = buildCommand();
        if (cmd == null) {
            return;
        }
        resultText = "等待结果...";
        resultPosition = null;
        resultBox.setValue(resultText);
        // Armed after execute(): its own chat echo must not be taken for the reply. The reply stays
        // in chat (false): vanilla makes its coordinates clickable, which the player may still want.
        CommandExecutor.execute(cmd);
        ChatResultCapture.awaitKeyed(LOCATE_REPLY_KEYS, false, message -> {
            resultText = message.getString();
            resultPosition = LocateResult.parse(resultText).orElse(null);
            if (resultBox != null) {
                resultBox.setValue(resultText);
            }
        });
    }

    private String buildCommand() {
        if (targetId == null) {
            return null;
        }
        return CommandBuilders.locate(COMMAND_KEYWORD.get(type), targetId);
    }

    /** Asks the server for real completions, same as pressing Tab after "/locate structure " in
     *  chat — this includes every mod's structures/POIs, not just vanilla's. Triggers exactly
     *  one screen rebuild once the (async, network round-trip) answer comes back. */
    private void fetchServerIds(LocateType requestedType) {
        String partial = "locate " + COMMAND_KEYWORD.get(requestedType) + " ";
        CommandSuggestionQuery.suggest(partial).thenAccept(suggestions -> {
            List<String> ids = suggestions.stream()
                    .filter(s -> !s.startsWith("#"))
                    .sorted()
                    .collect(Collectors.toList());
            if (ids.isEmpty() || this.minecraft.screen != this || this.type != requestedType) {
                return;
            }
            this.minecraft.setScreen(new LocateScreen(parent, requestedType, this.targetId, ids));
        });
    }

    private List<String> idsFor(LocateType type) {
        if (type != LocateType.BIOME) {
            if (serverIds != null) {
                return serverIds;
            }
            return type == LocateType.STRUCTURE ? VANILLA_STRUCTURES : VANILLA_POI_TYPES;
        }
        var level = this.minecraft.level;
        if (level == null) {
            return List.of();
        }
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            return registry.keySet().stream().map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Every id gets a Chinese label where one can be found; see {@link LocateLabeler} for how. */
    private Map<String, String> labelsFor(LocateType type, List<String> ids) {
        Map<String, String> labels = new HashMap<>();
        for (String id : ids) {
            labels.put(id, labeler.label(kind(), id));
        }
        return labels;
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        guiGraphics.drawString(this.font, "类型", centerX - 190, 46, COLOR_MUTED, false);
        guiGraphics.drawString(this.font, "目标", centerX - 190, 72, COLOR_MUTED, false);
        if (nameBox != null) {
            guiGraphics.drawString(this.font, "名称", centerX - 190, nameBox.getY() + 5, COLOR_MUTED, false);
        }
        guiGraphics.drawString(this.font, "结果（会显示服务器返回的原文，可能包含到该处的距离）", centerX - 150, resultLabelY, COLOR_MUTED, false);
        if (teleportButton != null) {
            teleportButton.active = resultPosition != null;
        }
        if (resultPosition != null) {
            String where = "X " + resultPosition.x() + (resultPosition.y() != null ? "  Y " + resultPosition.y() : "")
                    + "  Z " + resultPosition.z()
                    + (resultPosition.y() == null ? "（高度未知，传送时自动落到地面）" : "");
            guiGraphics.drawString(this.font, "目标坐标：" + where, centerX - 150, resultLabelY + 58, COLOR_ACCENT, false);
        }
    }
}
