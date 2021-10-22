package net.runelite.client.plugins.devtools;import com.google.common.collect.HashMultimap;import com.google.common.collect.ImmutableSet;import com.google.common.collect.Multimap;import lombok.extern.slf4j.Slf4j;import net.runelite.api.*;import net.runelite.api.coords.LocalPoint;import net.runelite.api.coords.WorldPoint;import net.runelite.api.events.*;import net.runelite.client.callback.ClientThread;import net.runelite.client.eventbus.EventBus;import net.runelite.client.eventbus.Subscribe;import net.runelite.client.ui.ColorScheme;import net.runelite.client.ui.DynamicGridLayout;import net.runelite.client.ui.FontManager;import org.jetbrains.annotations.NotNull;import javax.inject.Inject;import javax.swing.*;import javax.swing.border.CompoundBorder;import java.awt.*;import java.awt.event.AdjustmentEvent;import java.awt.event.AdjustmentListener;import java.util.*;import java.util.List;/** * @author Kris | 22/10/2021 */@SuppressWarnings("DuplicatedCode")@Slf4jpublic class EventInspector extends DevToolsFrame {    private final static int MAX_LOG_ENTRIES = 10_000;    private static final int VARBITS_ARCHIVE_ID = 14;    private final Client client;    private final EventBus eventBus;    private final ProjectileTracker projectileTracker;    private final JPanel tracker = new JPanel();    private int lastTick = 0;    private final Map<Skill, Integer> cachedExperienceMap = new HashMap<>();    private final List<OverheadTextChanged> overheadChatList = new ArrayList<>();    private final ClientThread clientThread;    private int[] oldVarps = null;    private int[] oldVarps2 = null;    private Multimap<Integer, Integer> varbits;    private final Set<Actor> facedActors = new HashSet<>();    private final Set<Actor> facedDirectionActors = new HashSet<>();    private PendingSpawnUpdated latestPendingSpawn = null;    /* A set for ignored scripts. There are some plugins which invoke procs through the client which we ignore. */    private final Set<Integer> ignoredClientScripts = ImmutableSet.<Integer>builder().add(4029).build();    private final JCheckBox projectiles = new JCheckBox("Projectiles", true);    private final JCheckBox spotanims = new JCheckBox("Spotanims", true);    private final JCheckBox sequences = new JCheckBox("Sequences", true);    private final JCheckBox soundEffects = new JCheckBox("Sound effects", true);    private final JCheckBox areaSoundEffects = new JCheckBox("Area Sound Effects", true);    private final JCheckBox say = new JCheckBox("Say", true);    private final JCheckBox experience = new JCheckBox("Experience", true);    private final JCheckBox messages = new JCheckBox("Messages", true);    private final JCheckBox varbitsCheckBox = new JCheckBox("Varbits", true);    private final JCheckBox varpsCheckBox = new JCheckBox("Varps", true);    private final JCheckBox hitsCheckBox = new JCheckBox("Hits", true);    private final JCheckBox interacting = new JCheckBox("Entity facing", true);    private final JCheckBox tileFacing = new JCheckBox("Tile facing", true);    private final JCheckBox clientScripts = new JCheckBox("Clientscripts", true);    private final JCheckBox exactMove = new JCheckBox("Exact Move", true);    private final JCheckBox combinedObjects = new JCheckBox("Combined Objects", true);    @Inject    EventInspector(Client client, EventBus eventBus, ClientThread clientThread, ProjectileTracker projectileTracker) {        this.client = client;        this.eventBus = eventBus;        this.clientThread = clientThread;        this.projectileTracker = projectileTracker;        setTitle("Event Inspector");        setLayout(new BorderLayout());        tracker.setLayout(new DynamicGridLayout(0, 1, 0, 3));        final JPanel trackerWrapper = new JPanel();        trackerWrapper.setLayout(new BorderLayout());        trackerWrapper.add(tracker, BorderLayout.NORTH);        final JScrollPane trackerScroller = new JScrollPane(trackerWrapper);        /* Even though height is defined as 100 here, it seems to prefer a height that is significantly bigger after the layout is applied. 100 turns to 655. */        trackerScroller.setPreferredSize(new Dimension(1400, 100));        final JScrollBar vertical = trackerScroller.getVerticalScrollBar();        vertical.addAdjustmentListener(new AdjustmentListener() {            int lastMaximum = actualMax();            private int actualMax() {                return vertical.getMaximum() - vertical.getModel().getExtent();            }            @Override            public void adjustmentValueChanged(AdjustmentEvent e) {                if (vertical.getValue() >= lastMaximum) {                    vertical.setValue(actualMax());                }                lastMaximum = actualMax();            }        });        add(trackerScroller, BorderLayout.CENTER);        final JPanel trackerOpts = new JPanel();        trackerOpts.setLayout(new WrapLayout());        trackerOpts.add(projectiles);        projectiles.setToolTipText("<html>The projectile inspector will require each unique projectile to be received from two" +                "<br>different distances in order for it to be able to identify all of the projectile parameters." +                "<br>This is due to one of the properties of projectile being the equivalent of" +                "<br>lengthAdjustment + (chebyshevDistance * stepMultiplier).</html>");        trackerOpts.add(spotanims);        trackerOpts.add(sequences);        trackerOpts.add(soundEffects);        trackerOpts.add(areaSoundEffects);        trackerOpts.add(say);        trackerOpts.add(messages);        trackerOpts.add(experience);        trackerOpts.add(varpsCheckBox);        trackerOpts.add(varbitsCheckBox);        trackerOpts.add(hitsCheckBox);        trackerOpts.add(interacting);        trackerOpts.add(tileFacing);        trackerOpts.add(clientScripts);        trackerOpts.add(exactMove);        trackerOpts.add(combinedObjects);        combinedObjects.setToolTipText("<html>Combined Objects refer to objects which have their models merged with the players' model" +                " to fix model priority issues.<br>This is commonly used for agility shortcuts and obstacles, such as pipes.</html>");        final JButton clearBtn = new JButton("Clear");        clearBtn.addActionListener(e -> {            tracker.removeAll();            tracker.revalidate();        });        trackerOpts.add(clearBtn);        final JButton enabledAllButton = new JButton("Enable all");        enabledAllButton.addActionListener(e -> {            for (Component component : trackerOpts.getComponents()) {                if (component instanceof JCheckBox) {                    ((JCheckBox) component).setSelected(true);                }            }            tracker.revalidate();        });        trackerOpts.add(enabledAllButton);        final JButton disableAllButton = new JButton("Disable all");        disableAllButton.addActionListener(e -> {            for (Component component : trackerOpts.getComponents()) {                if (component instanceof JCheckBox) {                    ((JCheckBox) component).setSelected(false);                }            }            tracker.revalidate();        });        trackerOpts.add(disableAllButton);        add(trackerOpts, BorderLayout.SOUTH);        pack();    }    private void addLine(String prefix, String text) {        int tick = client.getTickCount();        SwingUtilities.invokeLater(() -> {            if (tick != lastTick) {                lastTick = tick;                JLabel header = new JLabel("Tick " + tick);                header.setFont(FontManager.getRunescapeSmallFont());                header.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR),                        BorderFactory.createEmptyBorder(3, 6, 0, 0)));                tracker.add(header);            }            JPanel labelPanel = new JPanel();            labelPanel.setLayout(new BorderLayout());            JTextField prefixLabel = new JTextField(prefix);            prefixLabel.setEditable(false);            prefixLabel.setBackground(null);            prefixLabel.setBorder(null);            prefixLabel.setToolTipText(prefix);            JTextField textLabel = new JTextField(text);            textLabel.setEditable(false);            textLabel.setBackground(null);            textLabel.setBorder(null);            prefixLabel.setPreferredSize(new Dimension(400, 14));            prefixLabel.setMaximumSize(new Dimension(400, 14));            labelPanel.add(prefixLabel, BorderLayout.WEST);            labelPanel.add(textLabel);            tracker.add(labelPanel);            // Cull very old stuff            while (tracker.getComponentCount() > MAX_LOG_ENTRIES) {                tracker.remove(0);            }            tracker.revalidate();        });    }    @Subscribe    public void onProjectileMoved(ProjectileMoved event) {        if (!projectiles.isSelected()) return;        projectileTracker.submitProjectileMoved(client, event, (earlyProjectileInfo, dynamicProjectileInfo, prefix, text) -> addLine(prefix, text));    }    @Subscribe    public void spotanimChanged(GraphicChanged event) {        if (!spotanims.isSelected()) return;        Actor actor = event.getActor();        if (actor == null) return;        String actorLabel = formatActor(actor);        StringBuilder graphicsLabelBuilder = new StringBuilder();        graphicsLabelBuilder.append("Graphics(");        graphicsLabelBuilder.append("id = ").append(actor.getGraphic() == 65535 ? -1 : actor.getGraphic());        final int delay = actor.getGraphicStartCycle() - client.getGameCycle();        if (delay != 0) graphicsLabelBuilder.append(", delay = ").append(delay);        if (actor.getGraphicHeight() != 0) graphicsLabelBuilder.append(", height = ").append(actor.getGraphicHeight());        graphicsLabelBuilder.append(")");        addLine(actorLabel, graphicsLabelBuilder.toString());    }    @Subscribe    public void sequenceChanged(AnimationFrameIndexChanged event) {        if (!sequences.isSelected()) return;        Actor actor = event.getActor();        if (actor == null || actor.getAnimationFrameIndex() != 0 || actor.getName() == null || isActorPositionUninitialized(actor)) return;        String actorLabel = formatActor(actor);        StringBuilder animationLabelBuilder = new StringBuilder();        animationLabelBuilder.append("Animation(");        animationLabelBuilder.append("id = ").append(actor.getAnimation() == 65535 ? -1 : actor.getAnimation());        if (actor.getAnimationDelay() != 0) animationLabelBuilder.append(", delay = ").append(actor.getAnimationDelay());        animationLabelBuilder.append(")");        addLine(actorLabel, animationLabelBuilder.toString());    }    @Subscribe    public void soundEffectPlayed(SoundEffectPlayed event) {        if (!soundEffects.isSelected()) return;        final int soundId = event.getSoundId();        final int delay = event.getDelay();        final int loops = event.getLoops();        StringBuilder soundEffectBuilder = new StringBuilder();        soundEffectBuilder.append("SoundEffect(");        soundEffectBuilder.append("id = ").append(soundId);        if (delay != 0) soundEffectBuilder.append(", delay = ").append(delay);        if (loops != 1) soundEffectBuilder.append(", repetitions = ").append(loops);        soundEffectBuilder.append(")");        addLine("Local", soundEffectBuilder.toString());    }    @Subscribe    public void areaSoundEffectPlayed(AreaSoundEffectPlayed event) {        if (!areaSoundEffects.isSelected()) return;        /* Animation-driven sounds will always have the source set to non-null, however that information is useless to us so skip it. */        if (event.getSource() != null) return;        final int soundId = event.getSoundId();        final int delay = event.getDelay();        final int loops = event.getLoops();        final int radius = event.getRange();        StringBuilder soundEffectBuilder = new StringBuilder();        soundEffectBuilder.append("SoundEffect(");        soundEffectBuilder.append("id = ").append(soundId);        if (radius != 0) soundEffectBuilder.append(", radius = ").append(radius);        if (delay != 0) soundEffectBuilder.append(", delay = ").append(delay);        if (loops != 1) soundEffectBuilder.append(", repetitions = ").append(loops);        soundEffectBuilder.append(")");        WorldPoint location = WorldPoint.fromLocal(client, LocalPoint.fromScene(event.getSceneX(), event.getSceneY()));        Optional<Player> sourcePlayer = client.getPlayers().stream().filter(player -> player.getWorldLocation().distanceTo(location) == 0).findAny();        Optional<NPC> sourceNpc = client.getNpcs().stream().filter(npc -> npc.getWorldLocation().distanceTo(location) == 0).findAny();        if (sourcePlayer.isPresent() && sourceNpc.isEmpty()) {            addLine(formatActor(sourcePlayer.get()), soundEffectBuilder.toString());        } else if (sourceNpc.isPresent() && sourcePlayer.isEmpty()) {            addLine(formatActor(sourceNpc.get()), soundEffectBuilder.toString());        } else {            addLine("Unknown(" + "x: " + location.getX() + ", y: " + location.getY() + ")", soundEffectBuilder.toString());        }    }    @Subscribe    public void overheadTextChanged(OverheadTextChanged event) {        if (!say.isSelected()) return;        Actor actor = event.getActor();        if (actor == null) return;        overheadChatList.add(event);    }    /**     * Due to the annoying nature of how overhead chat is handled by the client, the only way we can detect if a message was actually server-driven     * or player-driven is to see if another field was changed shortly after. This strictly applies for player public chat, therefore it     * works great as a means to detect overhead chat messages.     */    @Subscribe    public void showPublicPlayerChatChanged(ShowPublicPlayerChatChanged event) {        if (!overheadChatList.isEmpty()) {            OverheadTextChanged element = overheadChatList.get(overheadChatList.size() - 1);            overheadChatList.remove(element);            log.info("Filtered player-driven overhead chat: " + element.getOverheadText());        }    }    @Subscribe    public void experienceChanged(StatChanged event) {        if (!experience.isSelected()) return;        final int previousExperience = cachedExperienceMap.getOrDefault(event.getSkill(), -1);        cachedExperienceMap.put(event.getSkill(), event.getXp());        if (previousExperience == -1) return;        final int experienceDiff = event.getXp() - previousExperience;        if (experienceDiff == 0) return;        addLine("Local", "Experience(skill = " + event.getSkill().getName() + ", xp = " + experienceDiff + ")");    }    @Subscribe    public void chatMessage(ChatMessage event) {        if (!messages.isSelected()) return;        ChatMessageType type = event.getType();        String name = event.getName();        if (name != null && !name.isEmpty()) {            log.info("Prevented chat message from being logged: " + event.getName() + ", " + type + ", " + event.getMessage());            return;        }        addLine("Local", "Message(type = " + type + ", text = \"" + event.getMessage() + "\")");    }    @Subscribe    public void onClientTick(ClientTick event) {        facedActors.clear();        facedDirectionActors.clear();        if (overheadChatList.isEmpty()) return;        for (OverheadTextChanged message : overheadChatList) {            String text = message.getOverheadText();            addLine(formatActor(message.getActor()), "Say(text = \"" + text + "\")");        }        overheadChatList.clear();    }    @Subscribe    public void onVarbitChanged(VarbitChanged varbitChanged) {        int index = varbitChanged.getIndex();        int[] varps = client.getVarps();        boolean isVarbit = false;        for (int i : varbits.get(index)) {            int old = client.getVarbitValue(oldVarps, i);            int newValue = client.getVarbitValue(varps, i);            String name = null;            for (Varbits varbit : Varbits.values()) {                if (varbit.getId() == i) {                    name = varbit.name();                    break;                }            }            if (old != newValue) {                client.setVarbitValue(oldVarps2, i, newValue);                if (varbitsCheckBox.isSelected()) {                    String prefix = name == null ? "Varbit" : ("Varbit \"" + name + "\"");                    addLine(prefix + " (varpId: " + index + ", oldValue: " + old + ")", "Varbit(id = " + i + ", value = " + newValue + ")");                }                isVarbit = true;            }        }        if (isVarbit || !varpsCheckBox.isSelected()) return;        int old = oldVarps2[index];        int newValue = varps[index];        if (old != newValue) {            String name = null;            for (VarPlayer varp : VarPlayer.values()) {                if (varp.getId() == index) {                    name = varp.name();                    break;                }            }            String prefix = name == null ? "Varp" : ("Varp \"" + name + "\"");            addLine(prefix + " (oldValue: " + old + ")", "Varp(id = " + index + ", value = " + newValue + ")");        }        System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);        System.arraycopy(client.getVarps(), 0, oldVarps2, 0, oldVarps2.length);    }    @Subscribe    public void onHitsplatApplied(HitsplatApplied event) {        if (!hitsCheckBox.isSelected()) return;        Actor actor = event.getActor();        if (actor == null || isActorPositionUninitialized(actor)) return;        Hitsplat hitsplat = event.getHitsplat();        addLine(formatActor(actor), "Hit(type = " + hitsplat.getHitsplatType() + ", amount = " + hitsplat.getAmount() + ")");    }    @Subscribe    public void onInteractingChanged(InteractingChanged event) {        if (!interacting.isSelected()) return;        Actor sourceActor = event.getSource();        if (!facedActors.add(sourceActor)) return;        Actor targetActor = event.getTarget();        if (sourceActor == null || isActorPositionUninitialized(sourceActor)) return;        addLine(formatActor(sourceActor), "FaceEntity(" + (targetActor == null ? "null" : formatActor(targetActor)) + ")");    }    @Subscribe    public void onFacedDirectionChanged(FacedDirectionChanged event) {        if (!tileFacing.isSelected() || event.getDirection() == -1) return;        Actor sourceActor = event.getSource();        if (!facedDirectionActors.add(sourceActor)) return;        if (sourceActor == null || isActorPositionUninitialized(sourceActor)) return;        addLine(formatActor(sourceActor), "FaceCoordinate(direction = " + event.getDirection() + ")");    }    @Subscribe    public void onScriptPreFired(ScriptPreFired event) {        if (!clientScripts.isSelected()) return;        ScriptEvent scriptEvent = event.getScriptEvent();        /* Filter out the non-server created scripts. Do note that other plugins may call CS2s, such as the quest helper plugin. */        if (scriptEvent == null || scriptEvent.getSource() != null || scriptEvent.type() != 76) return;        final Object[] arguments = scriptEvent.getArguments();        final int scriptId = Integer.parseInt(arguments[0].toString());        if (ignoredClientScripts.contains(scriptId)) return;        final StringBuilder args = new StringBuilder();        for (int i = 1; i < arguments.length; i++) {            final Object argument = arguments[i];            if (argument instanceof String) {                args.append('"').append(argument).append('"');            } else {                args.append(arguments[i]);            }            if (i < arguments.length - 1) args.append(", ");        }        addLine("Local", "ClientScript(id = " + scriptId + ", arguments = [" + args + "])");    }    @Subscribe    public void onExactMoveReceived(ExactMoveEvent event) {        if (!exactMove.isSelected()) return;        final Actor actor = event.getActor();        if (actor == null || isActorPositionUninitialized(actor)) return;        final int currentCycle = client.getGameCycle();        final StringBuilder exactMoveBuilder = new StringBuilder();        final WorldPoint actorWorldLocation = actor.getWorldLocation();        exactMoveBuilder.append("ExactMove(");        exactMoveBuilder.append("startLocation = Location(");        exactMoveBuilder.append(actorWorldLocation.getX() - event.getExactMoveDeltaX2()).append(", ");        exactMoveBuilder.append(actorWorldLocation.getY() - event.getExactMoveDeltaY2()).append(", ");        exactMoveBuilder.append(client.getPlane()).append("), ");        exactMoveBuilder.append("startDelay = ").append(event.getExactMoveArrive1Cycle() - currentCycle).append(", ");        exactMoveBuilder.append("endLocation = Location(");        exactMoveBuilder.append(actorWorldLocation.getX() - event.getExactMoveDeltaX1()).append(", ");        exactMoveBuilder.append(actorWorldLocation.getY() - event.getExactMoveDeltaY1()).append(", ");        exactMoveBuilder.append(client.getPlane()).append("), ");        exactMoveBuilder.append("endDelay = ").append(event.getExactMoveArrive2Cycle() - currentCycle).append(", ");        exactMoveBuilder.append("direction = ").append(event.getExactMoveDirection()).append(")");        addLine(formatActor(actor), exactMoveBuilder.toString());    }    @Subscribe    public void onPendingSpawnUpdated(PendingSpawnUpdated event) {        /* To get the model clip packet to function, we need to combine multiple plugins into what is essentially a state machine. */        latestPendingSpawn = event;    }    @SuppressWarnings("StringBufferReplaceableByString")    @Subscribe    public void onAttachedModelReceived(AttachedModelEvent event) {        if (!combinedObjects.isSelected()) return;        if (latestPendingSpawn == null) {            log.info("Latest pending spawn is null!");            return;        }        Scene scene = client.getScene();        Tile[][][] tiles = scene.getTiles();        Tile localTile = tiles[client.getPlane()][latestPendingSpawn.getX()][latestPendingSpawn.getY()];        /* Let's assume that any object that uses this packet is a main game object. Decorations and other objects can rarely ever be clicked, let alone this. */        Optional<GameObject> attachedObject = Arrays.stream(localTile.getGameObjects()).filter(obj -> {            final int rotation = obj.getOrientation().getAngle() >> 9;            final int type = obj.getFlags() & 0x1F;            return event.getAttachedModel() == getModel(obj, type, rotation, latestPendingSpawn.getX(), latestPendingSpawn.getY());        }).findAny();        if (attachedObject.isEmpty()) {            log.info("Unable to find a matching game object for object combine.");            return;        }        GameObject obj = attachedObject.get();        WorldPoint objectLocation = obj.getWorldLocation();        final int clientTime = client.getGameCycle();        final int rotation = obj.getOrientation().getAngle() >> 9;        final int type = obj.getFlags() & 0x1F;        final int minX = event.getMinX() - latestPendingSpawn.getX();        final int minY = event.getMinY() - latestPendingSpawn.getY();        final int maxX = event.getMaxX() - latestPendingSpawn.getX();        final int maxY = event.getMaxY() - latestPendingSpawn.getY();        final int startTime = event.getAnimationCycleStart() - clientTime;        final int endTime = event.getAnimationCycleEnd() - clientTime;        final StringBuilder attachedObjectBuilder = new StringBuilder();        attachedObjectBuilder.append("AttachedObject(");        attachedObjectBuilder.append("mapObject = MapObject(").append(obj.getId()).append(", ").append(type).append(", ").append(rotation).append(", ");        attachedObjectBuilder.append(objectLocation.getX()).append(", ").append(objectLocation.getY()).append(", ")                .append(objectLocation.getPlane()).append("), ");        attachedObjectBuilder.append("startTime = ").append(startTime).append(", ");        attachedObjectBuilder.append("endTime = ").append(endTime).append(", ");        attachedObjectBuilder.append("minX = ").append(minX).append(", ");        attachedObjectBuilder.append("maxX = ").append(maxX).append(", ");        attachedObjectBuilder.append("minY = ").append(minY).append(", ");        attachedObjectBuilder.append("maxY = ").append(maxY).append(")");        addLine(formatActor(event.getPlayer()), attachedObjectBuilder.toString());    }    private Model getModel(final GameObject obj, final int type, final int rotation, final int x, final int y) {        ObjectComposition def = client.getObjectDefinition(obj.getId());        int var19;        int var20;        if (rotation == 1 || rotation == 3) {            var19 = def.getSizeY();            var20 = def.getSizeX();        } else {            var19 = def.getSizeX();            var20 = def.getSizeY();        }        int var21 = x + (var19 >> 1);        int var22 = x + (var19 + 1 >> 1);        int var23 = y + (var20 >> 1);        int var24 = y + (var20 + 1 >> 1);        int[][] heights = client.getTileHeights()[obj.getPlane()];        int var26 = heights[var22][var24] + heights[var21][var24] + heights[var22][var23] + heights[var21][var23] >> 2;        int var27 = (x << 7) + (var19 << 6);        int var28 = (y << 7) + (var20 << 6);        return def.getModel(type, rotation, heights, var27, var26, var28);    }    /**     * It is possible for some variables to be uninitialized on login, so as an uber cheap fix, let's try-catch validate if the actor is fully initialized.     */    private boolean isActorPositionUninitialized(Actor actor) {        try {            return actor.getWorldLocation() == null;        } catch (NullPointerException ignored) {            return true;        }    }    private String formatActor(@NotNull Actor actor) {        WorldPoint actorWorldLocation = actor.getWorldLocation();        String coordinateString = "x: " + actorWorldLocation.getX() + ", y: " + actorWorldLocation.getY();        if (actor instanceof Player) {            return ("Player(" + (actor.getName() + ", idx: " + ((Player) actor).getPlayerId() + ", " + coordinateString + ")"));        } else if (actor instanceof NPC) {            return ("Npc(" + (actor.getName() + ", idx: " + ((NPC) actor).getIndex() + ", id: " + ((NPC) actor).getId() + ", " + coordinateString + ")"));        }        return ("Unknown(" + coordinateString + ")");    }    @Override    public void open() {        eventBus.register(this);        if (oldVarps == null) {            oldVarps = new int[client.getVarps().length];            oldVarps2 = new int[client.getVarps().length];        }        varbits = HashMultimap.create();        clientThread.invoke(() -> {            IndexDataBase indexVarbits = client.getIndexConfig();            final int[] varbitIds = indexVarbits.getFileIds(VARBITS_ARCHIVE_ID);            for (int id : varbitIds) {                VarbitComposition varbit = client.getVarbit(id);                if (varbit != null) {                    varbits.put(varbit.getIndex(), id);                }            }        });        super.open();    }    @Override    public void close() {        super.close();        tracker.removeAll();        eventBus.unregister(this);    }}