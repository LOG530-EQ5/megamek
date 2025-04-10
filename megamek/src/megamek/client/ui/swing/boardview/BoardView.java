/*
 * Copyright (c) 2000-2008 - Ben Mazur (bmazur@sev.org).
 * Copyright (c) 2018-2024 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package megamek.client.ui.swing.boardview;

import static megamek.client.ui.swing.tileset.HexTileset.HEX_H;
import static megamek.client.ui.swing.tileset.HexTileset.HEX_W;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalTheme;

import megamek.MMConstants;
import megamek.client.TimerSingleton;
import megamek.client.bot.princess.BotGeometry.ConvexBoardArea;
import megamek.client.bot.princess.PathEnumerator;
import megamek.client.bot.princess.Princess;
import megamek.client.event.BoardViewEvent;
import megamek.client.event.BoardViewListener;
import megamek.client.ui.IDisplayable;
import megamek.client.ui.Messages;
import megamek.client.ui.swing.ChatterBox2;
import megamek.client.ui.swing.ClientGUI;
import megamek.client.ui.swing.EntityChoiceDialog;
import megamek.client.ui.swing.GUIPreferences;
import megamek.client.ui.swing.tileset.TilesetManager;
import megamek.client.ui.swing.util.FontHandler;
import megamek.client.ui.swing.util.ImageCache;
import megamek.client.ui.swing.util.KeyBindReceiver;
import megamek.client.ui.swing.util.KeyCommandBind;
import megamek.client.ui.swing.util.MegaMekController;
import megamek.client.ui.swing.util.StringDrawer;
import megamek.client.ui.swing.util.UIUtil;
import megamek.client.ui.swing.widget.MegaMekBorder;
import megamek.client.ui.swing.widget.SkinSpecification;
import megamek.client.ui.swing.widget.SkinSpecification.UIComponents;
import megamek.client.ui.swing.widget.SkinXMLHandler;
import megamek.common.*;
import megamek.common.MovePath.MoveStepType;
import megamek.common.actions.ArtilleryAttackAction;
import megamek.common.actions.AttackAction;
import megamek.common.actions.EntityAction;
import megamek.common.actions.PhysicalAttackAction;
import megamek.common.actions.WeaponAttackAction;
import megamek.common.annotations.Nullable;
import megamek.common.event.*;
import megamek.common.options.GameOptions;
import megamek.common.options.OptionsConstants;
import megamek.common.pathfinder.BoardClusterTracker;
import megamek.common.pathfinder.BoardClusterTracker.BoardCluster;
import megamek.common.planetaryconditions.IlluminationLevel;
import megamek.common.planetaryconditions.PlanetaryConditions;
import megamek.common.preference.ClientPreferences;
import megamek.common.preference.IPreferenceChangeListener;
import megamek.common.preference.PreferenceChangeEvent;
import megamek.common.preference.PreferenceManager;
import megamek.common.util.ImageUtil;
import megamek.common.util.fileUtils.MegaMekFile;
import megamek.logging.MMLogger;
import megamek.server.props.OrbitalBombardment;

/**
 * Displays the board; lets the user scroll around and select points on it.
 */
public final class BoardView extends AbstractBoardView implements BoardListener, MouseListener,
        IPreferenceChangeListener, KeyBindReceiver {
    private static final MMLogger logger = MMLogger.create(BoardView.class);

    private static final int BOARD_HEX_CLICK = 1;
    private static final int BOARD_HEX_DOUBLECLICK = 2;
    private static final int BOARD_HEX_DRAG = 3;
    private static final int BOARD_HEX_POPUP = 4;

    // the dimensions of megamek's hex images
    public static final int HEX_DIAG = (int) Math.round(Math.sqrt(HEX_W * HEX_W + HEX_H * HEX_H));

    static final int HEX_WC = HEX_W - (HEX_W / 4);
    static final int HEX_ELEV = 12;

    private static final float[] ZOOM_FACTORS = { 0.30f, 0.41f, 0.50f, 0.60f,
            0.68f, 0.79f, 0.90f, 1.00f, 1.09f, 1.17f, 1.3f, 1.6f, 2.0f, 3.0f };

    private static final int[] ZOOM_SCALE_TYPES = {
            ImageUtil.IMAGE_SCALE_AVG_FILTER, ImageUtil.IMAGE_SCALE_AVG_FILTER,
            ImageUtil.IMAGE_SCALE_BICUBIC, ImageUtil.IMAGE_SCALE_BICUBIC,
            ImageUtil.IMAGE_SCALE_BICUBIC, ImageUtil.IMAGE_SCALE_BICUBIC,
            ImageUtil.IMAGE_SCALE_BICUBIC, ImageUtil.IMAGE_SCALE_BICUBIC,
            ImageUtil.IMAGE_SCALE_BICUBIC, ImageUtil.IMAGE_SCALE_BICUBIC,
            ImageUtil.IMAGE_SCALE_BICUBIC, ImageUtil.IMAGE_SCALE_BICUBIC,
            ImageUtil.IMAGE_SCALE_BICUBIC, ImageUtil.IMAGE_SCALE_BICUBIC };

    public static final int[] allDirections = { 0, 1, 2, 3, 4, 5 };

    // Set to TRUE to draw hexes with isometric elevation.
    private boolean drawIsometric = GUIPreferences.getInstance().getIsometricEnabled();

    int DROPSHDW_DIST = 20;

    // the index of zoom factor 1.00f
    static final int BASE_ZOOM_INDEX = 7;

    // Initial zoom index
    public int zoomIndex = BASE_ZOOM_INDEX;

    // line width of the c3 network lines
    static final int C3_LINE_WIDTH = 1;

    // line width of the fly over lines
    static final int FLY_OVER_LINE_WIDTH = 3;
    private static Font FONT_7 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 7);
    private static Font FONT_8 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 8);
    private static Font FONT_9 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 9);
    private static Font FONT_10 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 10);
    private static Font FONT_12 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 12);
    private static Font FONT_14 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 14);
    private static Font FONT_16 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 16);
    private static Font FONT_18 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 18);
    private static Font FONT_24 = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 24);

    Dimension hex_size;

    private Font font_note = FONT_10;
    private Font font_hexnum = FONT_10;
    private Font font_elev = FONT_9;
    private Font font_minefield = FONT_12;

    private final JPanel boardPanel = new BoardViewPanel(this);

    public final Game game;
    ClientGUI clientgui;

    private Dimension boardSize;

    // scrolly stuff:
    private JScrollPane scrollpane = null;
    private JScrollBar vbar;
    private JScrollBar hbar;
    private int scrollXDifference = 0;
    private int scrollYDifference = 0;
    // are we drag-scrolling?
    private boolean dragging = false;
    private boolean wantsPopup = false;

    /** True when the right mouse button was pressed to start a drag */
    private boolean shouldScroll = false;

    // entity sprites
    private Queue<EntitySprite> entitySprites = new PriorityQueue<>();
    private Queue<IsometricSprite> isometricSprites = new PriorityQueue<>();
    /**
     * A Map that maps an Entity ID and a secondary position to a Sprite. Note
     * that the key is a List where the first entry will be the Entity ID and
     * the second entry will be which secondary position the sprite belongs to;
     * if the Entity has no secondary positions, the first element will be the
     * ID and the second element will be -1.
     */
    private Map<ArrayList<Integer>, EntitySprite> entitySpriteIds = new HashMap<>();
    /**
     * A Map that maps an Entity ID and a secondary position to a Sprite. Note
     * that the key is a List where the first entry will be the Entity ID and
     * the second entry will be which secondary position the sprite belongs to;
     * if the Entity has no secondary positions, the first element will be the
     * ID and the second element will be -1.
     */
    private Map<ArrayList<Integer>, IsometricSprite> isometricSpriteIds = new HashMap<>();

    // sprites for the three selection cursors
    private CursorSprite cursorSprite;
    private CursorSprite highlightSprite;
    private CursorSprite selectedSprite;
    private CursorSprite firstLOSSprite;
    private CursorSprite secondLOSSprite;

    // sprite for current movement
    ArrayList<StepSprite> pathSprites = new ArrayList<>();
    ArrayList<FlightPathIndicatorSprite> fpiSprites = new ArrayList<FlightPathIndicatorSprite>();

    private ArrayList<Coords> strafingCoords = new ArrayList<>(5);

    // vector of sprites for all firing lines
    private ArrayList<AttackSprite> attackSprites = new ArrayList<>();

    // vector of sprites for all movement paths (using vectored movement)
    private ArrayList<MovementSprite> movementSprites = new ArrayList<>();

    // vector of sprites for C3 network lines
    private ArrayList<C3Sprite> c3Sprites = new ArrayList<>();

    // list of sprites for declared VTOL/airmek bombing/strafing targets
    private ArrayList<VTOLAttackSprite> vtolAttackSprites = new ArrayList<>();

    // vector of sprites for aero flyover lines
    private ArrayList<FlyOverSprite> flyOverSprites = new ArrayList<>();

    TilesetManager tileManager;

    // polygons for a few things
    private static final Polygon HEX_POLY;

    static {
        // hex polygon
        HEX_POLY = new Polygon();
        HEX_POLY.addPoint(21, 0);
        HEX_POLY.addPoint(62, 0);
        HEX_POLY.addPoint(83, 35);
        HEX_POLY.addPoint(83, 36);
        HEX_POLY.addPoint(62, 71);
        HEX_POLY.addPoint(21, 71);
        HEX_POLY.addPoint(0, 36);
        HEX_POLY.addPoint(0, 35);
    }

    Shape[] movementPolys;
    Shape[] facingPolys;
    Shape[] finalFacingPolys;
    Shape upArrow;
    Shape downArrow;

    // Image to hold the complete board shadow map
    BufferedImage shadowMap;

    // the player who owns this BoardView's client
    private Player localPlayer = null;

    /**
     * Stores the currently deploying entity, used for highlighting deployment
     * hexes.
     */
    private Entity en_Deployer = null;

    // should be able to turn it off(board editor)
    private boolean useLOSTool = true;

    // Initial scale factor for sprites and map
    float scale = 1.00f;
    private ImageCache<Integer, Image> scaledImageCache = new ImageCache<>();
    private ImageCache<Integer, BufferedImage> shadowImageCache = new ImageCache<>();

    private Set<Integer> animatedImages = new HashSet<>();

    // Move units step by step
    private ArrayList<MovingUnit> movingUnits = new ArrayList<>();

    private long moveWait = 0;

    // moving entity sprites
    private ArrayList<MovingEntitySprite> movingEntitySprites = new ArrayList<>();
    private HashMap<Integer, MovingEntitySprite> movingEntitySpriteIds = new HashMap<>();
    private ArrayList<GhostEntitySprite> ghostEntitySprites = new ArrayList<>();

    // wreck sprites
    private ArrayList<WreckSprite> wreckSprites = new ArrayList<>();
    private ArrayList<IsometricWreckSprite> isometricWreckSprites = new ArrayList<>();

    private Coords rulerStart;
    private Coords rulerEnd;
    private Color rulerStartColor;
    private Color rulerEndColor;

    private Coords lastCursor;
    private Coords highlighted;
    Coords selected;
    private Coords firstLOS;

    /** stores the theme last selected to override all hex themes */
    private String selectedTheme = null;

    // hexes with ECM effect
    private Map<Coords, Color> ecmHexes = null;
    // hexes that are teh centers of ECM effects
    private Map<Coords, Color> ecmCenters = null;
    // hexes with ECM effect
    private Map<Coords, Color> eccmHexes = null;
    // hexes that are teh centers of ECCM effects
    private Map<Coords, Color> eccmCenters = null;

    // reference to our timertask for redraw
    private TimerTask redrawTimerTask;

    BufferedImage bvBgImage = null;
    boolean bvBgShouldTile = false;
    BufferedImage scrollPaneBgBuffer = null;
    Image scrollPaneBgImg = null;

    private static final int FRAMES = 24;
    private long totalTime;
    private long averageTime;
    private int frameCount;
    private Font fpsFont = new Font(MMConstants.FONT_SANS_SERIF, Font.PLAIN, 20);

    /**
     * Keeps track of whether we have an active ChatterBox2
     */
    private boolean chatterBoxActive = false;

    /**
     * Keeps track of whether an outside source tells the BoardView that it
     * should ignore keyboard commands.
     */
    private boolean shouldIgnoreKeys = false;

    private FovHighlightingAndDarkening fovHighlightingAndDarkening;

    private String FILENAME_RADAR_BLIP_IMAGE = "radarBlip.png";
    private Image radarBlipImage;

    /**
     * Cache that stores hex images for different coords
     */
    ImageCache<Coords, HexImageCacheEntry> hexImageCache;

    /**
     * Keeps track of whether all deployment zones should
     * be shown in the Arty Auto Hit Designation phase
     */
    public boolean showAllDeployment = false;

    private boolean showLobbyPlayerDeployment = false;

    private long paintCompsStartTime;

    private Rectangle displayablesRect = new Rectangle();

    // Soft Centering ---

    /** True when the board is in the process of centering to a spot. */
    private boolean isSoftCentering = false;
    /**
     * The final position of a soft centering relative to board size (x, y = 0...1).
     */
    private Point2D softCenterTarget = new Point2D.Double();
    private Point2D oldCenter = new Point2D.Double();
    private long waitTimer;
    /** Speed of soft centering of the board, less is faster */
    private static final int SOFT_CENTER_SPEED = 8;

    // Tooltip Info ---
    /**
     * Holds the final Coords for a planned movement. Set by MovementDisplay,
     * used to display the distance in the board tooltip.
     */
    private Coords movementTarget;

    // Used to track the previous x/y for tooltip display
    int prevTipX = -1, prevTipY = -1;

    /**
     * Flag to indicate if we should display information about illegal terrain in
     * hexes.
     */
    boolean displayInvalidHexInfo = false;

    /**
     * Stores the correct tooltip dismiss delay so it can be restored when exiting
     * the boardview
     */
    private int dismissDelay = ToolTipManager.sharedInstance().getDismissDelay();

    /** The coords where the mouse was last. */
    Coords lastCoords;

    private GUIPreferences GUIP = GUIPreferences.getInstance();

    private final TerrainShadowHelper shadowHelper = new TerrainShadowHelper(this);

    private final StringDrawer invalidString = new StringDrawer(Messages.getString("BoardEditor.INVALID"))
            .color(GUIP.getWarningColor()).font(FontHandler.notoFont().deriveFont(Font.BOLD)).center();

    BoardViewTooltipProvider boardViewToolTip = (point, movementTarget) -> null;

    // Part of the sprites need specialized treatment; as there can be many sprites,
    // filtering them
    // on the spot is a noticeable performance hit (in iso mode), therefore the
    // sprites are copied
    // to specialized lists when created
    private final TreeSet<Sprite> overTerrainSprites = new TreeSet<>();
    private final TreeSet<HexSprite> behindTerrainHexSprites = new TreeSet<>();

    /**
     * Construct a new board view for the specified game
     */
    public BoardView(final Game game, final MegaMekController controller, @Nullable ClientGUI clientgui)
            throws java.io.IOException {
        this.game = game;
        this.clientgui = clientgui;

        if (GUIP == null) {
            GUIP = GUIPreferences.getInstance();
        }

        hexImageCache = new ImageCache<>();

        tileManager = new TilesetManager(game);
        ToolTipManager.sharedInstance().registerComponent(boardPanel);

        game.addGameListener(gameListener);
        game.getBoard().addBoardListener(this);

        redrawTimerTask = scheduleRedrawTimer(); // call only once
        clearSprites();
        boardPanel.addMouseListener(this);
        boardPanel.addMouseWheelListener(we -> {
            Point mousePoint = we.getPoint();
            Point dispPoint = new Point(mousePoint.x + boardPanel.getBounds().x,
                    mousePoint.y + boardPanel.getBounds().y);

            // If the mouse is over an IDisplayable, have it react instead of the board
            // Currently only implemented for the ChatterBox
            for (IDisplayable disp : overlays) {
                if (!(disp instanceof ChatterBox2)) {
                    continue;
                }
                double width = scrollpane.getViewport().getSize().getWidth();
                double height = scrollpane.getViewport().getSize().getHeight();
                Dimension drawDimension = new Dimension();
                drawDimension.setSize(width, height);
                // we need to adjust the point, because it should be against
                // the displayable dimension
                if (disp.isMouseOver(dispPoint, drawDimension)) {
                    ChatterBox2 cb2 = (ChatterBox2) disp;
                    if (we.getWheelRotation() > 0) {
                        cb2.scrollDown();
                    } else {
                        cb2.scrollUp();
                    }
                    refreshDisplayables();
                    return;
                }
            }

            // calculate a few things to reposition the map
            Coords zoomCenter = getCoordsAt(we.getPoint());
            Point hexL = getCentreHexLocation(zoomCenter);
            Point inhexDelta = new Point(we.getPoint());
            inhexDelta.translate(-HEX_W, -HEX_H);
            inhexDelta.translate(-hexL.x, -hexL.y);
            double ihdx = ((double) inhexDelta.x) / ((double) HEX_W) / scale;
            double ihdy = ((double) inhexDelta.y) / ((double) HEX_H) / scale;
            int oldzoomIndex = zoomIndex;

            boolean ZoomNoCtrl = GUIP.getMouseWheelZoom();
            boolean wheelFlip = GUIP.getMouseWheelZoomFlip();
            boolean zoomIn = (we.getWheelRotation() > 0) ^ wheelFlip; // = XOR
            boolean doZoom = ZoomNoCtrl ^ we.isControlDown(); // = XOR
            boolean horizontalScroll = !doZoom && we.isShiftDown();

            if (doZoom) {
                if (zoomIn) {
                    zoomIn();
                } else {
                    zoomOut();
                }

                if (zoomIndex != oldzoomIndex) {
                    adjustVisiblePosition(zoomCenter, dispPoint, ihdx, ihdy);
                }
            } else {
                // SCROLL
                if (horizontalScroll) {
                    hbar.setValue((int) (hbar.getValue() + (HEX_H * scale * (we.getWheelRotation()))));
                } else {
                    vbar.setValue((int) (vbar.getValue() + (HEX_H * scale * (we.getWheelRotation()))));
                }
                stopSoftCentering();
            }

            pingMinimap();
        });

        MouseMotionListener mouseMotionListener = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point point = e.getPoint();
                for (IDisplayable disp : overlays) {
                    if (disp.isBeingDragged()) {
                        return;
                    }
                    double width = Math.min(boardSize.getWidth(), scrollpane.getViewport().getSize().getWidth());
                    double height = Math.min(boardSize.getHeight(), scrollpane.getViewport().getSize().getHeight());
                    Dimension drawDimension = new Dimension();
                    drawDimension.setSize(width, height);
                    disp.isMouseOver(point, drawDimension);
                }

                // Reset popup flag if the user moves their mouse away
                wantsPopup = false;

                final Coords mcoords = getCoordsAt(point);
                if (!mcoords.equals(lastCoords) && game.getBoard().contains(mcoords)) {
                    lastCoords = mcoords;
                    boardPanel.setToolTipText(boardViewToolTip.getTooltip(e, movementTarget));
                } else if (!game.getBoard().contains(mcoords)) {
                    boardPanel.setToolTipText(null);
                } else {
                    if (prevTipX > 0 && prevTipY > 0) {
                        int deltaX = point.x - prevTipX;
                        int deltaY = point.y - prevTipY;
                        double deltaMagnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        if (deltaMagnitude > GUIP.getTooltipDistSuppression()) {
                            prevTipX = -1;
                            prevTipY = -1;
                            // Set the dismissal delay to 0 so that the tooltip goes away and does not
                            // reappear
                            // until the mouse has moved more than the suppression distance
                            ToolTipManager.sharedInstance().setDismissDelay(0);
                            // and then, when the tooltip has gone away, reset the dismiss delay
                            SwingUtilities.invokeLater(() -> {
                                if (GUIP.getTooltipDismissDelay() >= 0) {
                                    ToolTipManager.sharedInstance().setDismissDelay(GUIP.getTooltipDismissDelay());
                                } else {
                                    ToolTipManager.sharedInstance().setDismissDelay(dismissDelay);
                                }
                            });
                        }
                    }
                    prevTipX = point.x;
                    prevTipY = point.y;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point point = e.getPoint();
                for (IDisplayable disp : overlays) {
                    Point adjustPoint = new Point((int) Math.min(boardSize.getWidth(), -boardPanel.getBounds().getX()),
                            (int) Math.min(boardSize.getHeight(), -boardPanel.getBounds().getY()));
                    Point dispPoint = new Point();
                    dispPoint.x = point.x - adjustPoint.x;
                    dispPoint.y = point.y - adjustPoint.y;
                    double width = Math.min(boardSize.getWidth(), scrollpane.getViewport().getSize().getWidth());
                    double height = Math.min(boardSize.getHeight(), scrollpane.getViewport().getSize().getHeight());
                    Dimension drawDimension = new Dimension();
                    drawDimension.setSize(width, height);
                    if (disp.isDragged(dispPoint, drawDimension)) {
                        boardPanel.repaint();
                        return;
                    }
                }
                // only scroll when we should
                if (!shouldScroll) {
                    mouseAction(getCoordsAt(point), BOARD_HEX_DRAG, e.getModifiersEx(), e.getButton());
                    return;
                }
                // if we have not yet been dragging, set the var so popups don't
                // appear when we stop scrolling
                if (!dragging) {
                    dragging = true;
                    boardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                Point p = scrollpane.getViewport().getViewPosition();
                int newX = p.x - (e.getX() - scrollXDifference);
                int newY = p.y - (e.getY() - scrollYDifference);
                int maxX = boardPanel.getWidth() - scrollpane.getViewport().getWidth();
                int maxY = boardPanel.getHeight() - scrollpane.getViewport().getHeight();
                if (newX < 0) {
                    newX = 0;
                }

                if (newX > maxX) {
                    newX = maxX;
                }

                if (newY < 0) {
                    newY = 0;
                }

                if (newY > maxY) {
                    newY = maxY;
                }
                // don't scroll horizontally if the board fits into the window
                if (scrollpane.getViewport().getWidth() >= boardPanel.getWidth()) {
                    newX = scrollpane.getViewport().getViewPosition().x;
                }
                scrollpane.getViewport().setViewPosition(new Point(newX, newY));
                pingMinimap();
            }
        };
        boardPanel.addMouseMotionListener(mouseMotionListener);

        if (controller != null) {
            registerKeyboardCommands(controller);
        }

        updateBoardSize();

        hex_size = new Dimension((int) (HEX_W * scale), (int) (HEX_H * scale));

        initPolys();

        cursorSprite = new CursorSprite(this, Color.cyan);
        highlightSprite = new CursorSprite(this, Color.white);
        selectedSprite = new CursorSprite(this, Color.blue);
        firstLOSSprite = new CursorSprite(this, Color.red);
        secondLOSSprite = new CursorSprite(this, Color.red);

        PreferenceManager.getClientPreferences().addPreferenceChangeListener(this);
        GUIP.addPreferenceChangeListener(this);
        KeyBindParser.addPreferenceChangeListener(this);

        SpecialHexDisplay.Type.ARTILLERY_MISS.init();
        SpecialHexDisplay.Type.ARTILLERY_HIT.init();
        SpecialHexDisplay.Type.ARTILLERY_DRIFT.init();
        SpecialHexDisplay.Type.ARTILLERY_INCOMING.init();
        SpecialHexDisplay.Type.ARTILLERY_TARGET.init();
        SpecialHexDisplay.Type.ARTILLERY_ADJUSTED.init();
        SpecialHexDisplay.Type.ARTILLERY_AUTOHIT.init();
        SpecialHexDisplay.Type.BOMB_MISS.init();
        SpecialHexDisplay.Type.BOMB_HIT.init();
        SpecialHexDisplay.Type.BOMB_DRIFT.init();
        SpecialHexDisplay.Type.PLAYER_NOTE.init();
        SpecialHexDisplay.Type.ORBITAL_BOMBARDMENT.init();
        SpecialHexDisplay.Type.ORBITAL_BOMBARDMENT_INCOMING.init();
        SpecialHexDisplay.Type.NUKE_HIT.init();
        SpecialHexDisplay.Type.NUKE_INCOMING.init();

        fovHighlightingAndDarkening = new FovHighlightingAndDarkening(this);

        radarBlipImage = ImageUtil.loadImageFromFile(
                new MegaMekFile(Configuration.miscImagesDir(), FILENAME_RADAR_BLIP_IMAGE).toString());
    }

    private void registerKeyboardCommands(final MegaMekController controller) {
        controller.registerCommandAction(KeyCommandBind.TOGGLE_CHAT, this, this::performChat);
        controller.registerCommandAction(KeyCommandBind.TOGGLE_CHAT_CMD, this, this::performChatCmd);
        controller.registerCommandAction(KeyCommandBind.CENTER_ON_SELECTED, this, this::centerOnSelected);

        controller.registerCommandAction(KeyCommandBind.SCROLL_NORTH, this::shouldReceiveKeyCommands,
                this::scrollNorth, this::pingMinimap);
        controller.registerCommandAction(KeyCommandBind.SCROLL_SOUTH, this::shouldReceiveKeyCommands,
                this::scrollSouth, this::pingMinimap);
        controller.registerCommandAction(KeyCommandBind.SCROLL_EAST, this::shouldReceiveKeyCommands,
                this::scrollEast, this::pingMinimap);
        controller.registerCommandAction(KeyCommandBind.SCROLL_WEST, this::shouldReceiveKeyCommands,
                this::scrollWest, this::pingMinimap);
    }

    private void scrollNorth() {
        vbar.setValue((int) (vbar.getValue() - (HEX_H * scale)));
        stopSoftCentering();
    }

    private void scrollSouth() {
        vbar.setValue((int) (vbar.getValue() + (HEX_H * scale)));
        stopSoftCentering();
    }

    private void scrollEast() {
        hbar.setValue((int) (hbar.getValue() + (HEX_W * scale)));
        stopSoftCentering();
    }

    private void scrollWest() {
        hbar.setValue((int) (hbar.getValue() - (HEX_W * scale)));
        stopSoftCentering();
    }

    private void performChatCmd() {
        if (!getChatterBoxActive()) {
            setChatterBoxActive(true);
            for (IDisplayable disp : overlays) {
                if (disp instanceof ChatterBox2) {
                    ((ChatterBox2) disp).slideUp();
                    ((ChatterBox2) disp).setMessage("/");
                }
            }
            boardPanel.requestFocus();
        }
    }

    private void performChat() {
        if (!getChatterBoxActive()) {
            setChatterBoxActive(true);
            for (IDisplayable disp : overlays) {
                if (disp instanceof ChatterBox2) {
                    ((ChatterBox2) disp).slideUp();
                }
            }
            boardPanel.requestFocus();
        }
    }

    @Override
    public boolean shouldReceiveKeyCommands() {
        return !getChatterBoxActive()
                && boardPanel.isVisible()
                && !game.getPhase().isLounge()
                && !shouldIgnoreKeys;
    }

    private final RedrawWorker redrawWorker = new RedrawWorker();

    /**
     * this should only be called once!! this will cause a timer to schedule
     * constant screen updates every 20 milliseconds!
     */
    private TimerTask scheduleRedrawTimer() {
        final TimerTask redraw = new TimerTask() {
            @Override
            public void run() {
                try {
                    SwingUtilities.invokeLater(redrawWorker);
                } catch (Exception ie) {
                    logger.error("Ignoring error: " + ie.getMessage());
                }
            }
        };
        TimerSingleton.getInstance().schedule(redraw, 20, 20);
        return redraw;
    }

    private void scheduleRedraw() {
        try {
            SwingUtilities.invokeLater(redrawWorker);
        } catch (Exception ie) {
            logger.error("Ignoring error: " + ie.getMessage());
        }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        switch (e.getName()) {
            case ClientPreferences.MAP_TILESET:
                clearHexImageCache();
                updateBoard();
                break;

            case GUIPreferences.UNIT_LABEL_STYLE:
                clientgui.systemMessage("Label style changed to " + GUIP.getUnitLabelStyle().description);
            case GUIPreferences.UNIT_LABEL_BORDER:
            case GUIPreferences.TEAM_COLORING:
            case GUIPreferences.SHOW_DAMAGE_DECAL:
            case GUIPreferences.SHOW_DAMAGE_LEVEL:
                updateEntityLabels();
                for (Sprite s : wreckSprites) {
                    s.prepare();
                }
                for (Sprite s : isometricWreckSprites) {
                    s.prepare();
                }
                break;

            case GUIPreferences.USE_CAMO_OVERLAY:
                getTilesetManager().reloadUnitIcons();
                break;

            case GUIPreferences.AOHEXSHADOWS:
            case GUIPreferences.FLOATINGISO:
            case GUIPreferences.LEVELHIGHLIGHT:
            case GUIPreferences.SHOW_COORDS:
            case GUIPreferences.FOV_DARKEN:
            case GUIPreferences.FOV_DARKEN_ALPHA:
            case GUIPreferences.FOV_GRAYSCALE:
            case GUIPreferences.FOV_HIGHLIGHT:
            case GUIPreferences.FOV_HIGHLIGHT_ALPHA:
            case GUIPreferences.FOV_STRIPES:
            case GUIPreferences.FOV_HIGHLIGHT_RINGS_COLORS_HSB:
            case GUIPreferences.FOV_HIGHLIGHT_RINGS_RADII:
            case GUIPreferences.SHADOWMAP:
                clearHexImageCache();
                getTilesetManager().reloadUnitIcons();
                boardPanel.repaint();
                break;

            case GUIPreferences.INCLINES:
                game.getBoard().initializeAllAutomaticTerrain();
                clearHexImageCache();
                boardPanel.repaint();
                break;
        }
    }

    void addMovingUnit(Entity entity, Vector<UnitLocation> movePath) {
        if (!movePath.isEmpty()) {
            MovingUnit m = new MovingUnit(entity, movePath);
            movingUnits.add(m);

            GhostEntitySprite ghostSprite = new GhostEntitySprite(this, entity);
            ghostEntitySprites.add(ghostSprite);

            // Center on the starting hex of the moving unit.
            UnitLocation loc = movePath.get(0);

            if (GUIP.getAutoCenter()) {
                centerOnHex(loc.getCoords());
            }
        }
    }

    @Override
    public void draw(Graphics g) {
        if (GUIP.getShowFPS()) {
            paintCompsStartTime = System.nanoTime();
        }

        UIUtil.setHighQualityRendering(g);

        Rectangle viewRect = scrollpane.getVisibleRect();

        if (!isTileImagesLoaded()) {
            MetalTheme theme = new DefaultMetalTheme();
            g.setColor(theme.getControl());
            g.fillRect(-boardPanel.getX(), -boardPanel.getY(), (int) viewRect.getWidth(),
                    (int) viewRect.getHeight());
            g.setColor(theme.getControlTextColor());
            g.drawString(Messages.getString("BoardView1.loadingImages"), 20, 50);
            if (!tileManager.isStarted()) {
                logger.info("Loading images for board");
                tileManager.loadNeededImages(game);
            }
            // wait 1 second, then repaint
            boardPanel.repaint(1000);
            return;
        }

        if (bvBgShouldTile && (bvBgImage != null)) {
            Rectangle clipping = g.getClipBounds();
            int x = 0;
            int y = 0;
            int w = bvBgImage.getWidth();
            int h = bvBgImage.getHeight();
            while (y < clipping.getHeight()) {
                int yRem = 0;
                if (y == 0) {
                    yRem = clipping.y % h;
                }
                x = 0;
                while (x < clipping.getWidth()) {
                    int xRem = 0;
                    if (x == 0) {
                        xRem = clipping.x % w;
                    }
                    if ((xRem > 0) || (yRem > 0)) {
                        try {
                            g.drawImage(bvBgImage.getSubimage(xRem, yRem, w - xRem, h - yRem),
                                    clipping.x + x, clipping.y + y, boardPanel);
                        } catch (Exception e) {
                            // if we somehow messed up the math, log the error and simply act as if we have
                            // no background image.
                            Rectangle rasterBounds = bvBgImage.getRaster().getBounds();

                            String errorData = String.format(
                                    "Error drawing background image. Raster Bounds: %.2f, %.2f, width:%.2f, height:%.2f, Attempted Draw Coordinates: %d, %d, width:%d, height:%d",
                                    rasterBounds.getMinX(), rasterBounds.getMinY(), rasterBounds.getWidth(),
                                    rasterBounds.getHeight(),
                                    xRem, yRem, w - xRem, h - yRem);
                            logger.error(errorData);
                        }
                    } else {
                        g.drawImage(bvBgImage, clipping.x + x, clipping.y + y,
                                boardPanel);
                    }
                    x += w - xRem;
                }
                y += h - yRem;
            }
        } else if (bvBgImage != null) {
            g.drawImage(bvBgImage, -boardPanel.getX(), -boardPanel.getY(), (int) viewRect.getWidth(),
                    (int) viewRect.getHeight(), boardPanel);
        } else {
            MetalTheme theme = new DefaultMetalTheme();
            g.setColor(theme.getControl());
            g.fillRect(-boardPanel.getX(), -boardPanel.getY(), (int) viewRect.getWidth(),
                    (int) viewRect.getHeight());
        }

        // Used to pad the board edge
        g.translate(HEX_W, HEX_H);

        // Initialize the shadow map when it's not yet present
        if (shadowMap == null) {
            shadowMap = shadowHelper.updateShadowMap();
        }

        drawHexes(g, g.getClipBounds());

        // draw wrecks
        if (GUIP.getShowWrecks() && !useIsometric()) {
            drawSprites(g, wreckSprites);
        }

        // Minefield signs all over the place!
        drawMinefields(g);

        // Artillery targets
        drawArtilleryHexes(g);

        // draw highlight border
        drawSprite(g, highlightSprite);

        // draw cursors
        drawSprite(g, cursorSprite);
        drawSprite(g, selectedSprite);
        drawSprite(g, firstLOSSprite);
        drawSprite(g, secondLOSSprite);

        // draw deployment indicators.
        // For Isometric rendering, this is done during drawHexes
        if ((en_Deployer != null) && !useIsometric()) {
            drawDeployment(g);
        }

        if ((game.getPhase().isSetArtilleryAutohitHexes() && showAllDeployment)
                || ((game.getPhase().isLounge()) && showLobbyPlayerDeployment)) {
            drawAllDeployment(g);
        }

        // draw C3 links
        drawSprites(g, c3Sprites);

        // draw flyover routes
        if (game.getBoard().onGround()) {
            drawSprites(g, vtolAttackSprites);
            drawSprites(g, flyOverSprites);
        }

        // draw moving onscreen entities
        drawSprites(g, movingEntitySprites);

        // draw ghost onscreen entities
        drawSprites(g, ghostEntitySprites);

        // draw onscreen attacks
        drawSprites(g, attackSprites);

        // draw movement vectors.
        if (game.useVectorMove() && game.getPhase().isMovement()) {
            drawSprites(g, movementSprites);
        }

        if (game.getPhase().isFiring()) {
            for (Coords c : strafingCoords) {
                drawHexBorder(g, getHexLocation(c), Color.yellow, 0, 3);
            }
        }

        if (!useIsometric()) {
            // In non-iso mode, all sprites can now be drawn according to their internal
            // priority (draw order)
            drawSprites(g, allSprites);
        } else {
            // In iso mode, some sprites are drawn in drawHexes so they can go behind
            // terrain; draw only the others here
            drawSprites(g, overTerrainSprites);
        }

        // draw movement, if valid
        drawSprites(g, pathSprites);

        // draw flight path indicators
        drawSprites(g, fpiSprites);

        // draw the ruler line
        if (rulerStart != null) {
            Point start = getCentreHexLocation(rulerStart);
            if (rulerEnd != null) {
                Point end = getCentreHexLocation(rulerEnd);
                g.setColor(Color.yellow);
                g.drawLine(start.x, start.y, end.x, end.y);

                g.setColor(rulerEndColor);
                g.fillRect(end.x - 1, end.y - 1, 2, 2);
            }

            g.setColor(rulerStartColor);
            g.fillRect(start.x - 1, start.y - 1, 2, 2);
        }

        // Undo the previous translation
        g.translate(-HEX_W, -HEX_H);

        // draw all the "displayables"
        if (displayablesRect == null) {
            displayablesRect = new Rectangle();
        }
        displayablesRect.x = -boardPanel.getX();
        displayablesRect.y = -boardPanel.getY();
        displayablesRect.width = scrollpane.getViewport().getViewRect().width;
        displayablesRect.height = scrollpane.getViewport().getViewRect().height;
        for (IDisplayable disp : overlays) {
            disp.draw(g, displayablesRect);
        }

        if (GUIP.getShowFPS()) {
            if (frameCount == FRAMES) {
                averageTime = totalTime / FRAMES;
                totalTime = 0;
                frameCount = 0;
            } else {
                totalTime += System.nanoTime() - paintCompsStartTime;
                frameCount++;
            }
            String s = String.format("%1$5.3f", averageTime / 1000000d);
            g.setFont(fpsFont);
            g.setColor(Color.YELLOW);
            g.drawString(s, -boardPanel.getX() + 5, -boardPanel.getY() + 20);
        }

        // debugging method that renders the bounding box of a unit's movement envelope.
        // renderClusters((Graphics2D) g);
        // renderMovementBoundingBox((Graphics2D) g);
        // renderDonut(g, new Coords(10, 10), 2);
        // renderApproxHexDirection((Graphics2D) g);
    }

    /**
     * Debugging method that renders a hex in the approximate direction
     * from the selected entity to the selected hex, of both exist.
     *
     * @param g Graphics object on which to draw.
     */
    @SuppressWarnings("unused")
    private void renderApproxHexDirection(Graphics2D g) {
        if (getSelectedEntity() == null || selected == null) {
            return;
        }

        int direction = getSelectedEntity().getPosition().approximateDirection(selected, 0, 0);

        Coords donutCoords = getSelectedEntity().getPosition().translated(direction);

        Point p = getCentreHexLocation(donutCoords.getX(), donutCoords.getY(), true);
        p.translate(HEX_W / 2, HEX_H / 2);
        drawHexBorder(g, p, Color.BLUE, 0, 6);
    }

    /**
     * Debugging method that renders the bounding hex of a unit's movement envelope.
     * Warning: very slow when rendering the bounding hex for really fast units.
     *
     * @param g Graphics object on which to draw.
     */
    @SuppressWarnings("unused")
    private void renderMovementBoundingBox(Graphics2D g) {
        if (getSelectedEntity() != null) {
            Princess princess = new Princess("test", MMConstants.LOCALHOST, 2020);
            princess.getGame().setBoard(this.game.getBoard());
            PathEnumerator pathEnum = new PathEnumerator(princess, this.game);
            pathEnum.recalculateMovesFor(this.getSelectedEntity());

            ConvexBoardArea cba = pathEnum.getUnitMovableAreas().get(this.getSelectedEntity().getId());
            for (int x = 0; x < game.getBoard().getWidth(); x++) {
                for (int y = 0; y < game.getBoard().getHeight(); y++) {
                    Point p = getCentreHexLocation(x, y, true);
                    p.translate(HEX_W / 2, HEX_H / 2);
                    Coords c = new Coords(x, y);

                    if (cba.contains(c)) {

                        drawHexBorder(g, p, Color.PINK, 0, 6);
                    }
                }
            }

            for (int x = 0; x < 6; x++) {
                Coords c = cba.getVertexNum(x);
                if (c == null) {
                    continue;
                }

                Point p = getCentreHexLocation(c.getX(), c.getY(), true);
                p.translate(HEX_W / 2, HEX_H / 2);

                drawHexBorder(g, p, Color.yellow, 0, 3);
                new StringDrawer(Integer.toString(x)).at(p).center().color(Color.YELLOW).draw(g);
            }
        }
    }

    /**
     * Debugging method that renders a hex donut around the given coordinates, with
     * the given radius.
     *
     * @param g Graphics object on which to draw.
     */
    @SuppressWarnings("unused")
    private void renderDonut(Graphics2D g, Coords coords, int radius) {
        ArrayList<Coords> donut = coords.allAtDistance(radius);

        for (Coords donutCoords : donut) {
            Point p = getCentreHexLocation(donutCoords.getX(), donutCoords.getY(), true);
            p.translate(HEX_W / 2, HEX_H / 2);
            drawHexBorder(g, p, Color.PINK, 0, 6);
        }
    }

    /**
     * Debugging method that renders a obnoxious pink lines around hexes in "Board
     * Clusters"
     *
     * @param g Graphics object on which to draw.
     */
    @SuppressWarnings("unused")
    private void renderClusters(Graphics2D g) {
        BoardClusterTracker bct = new BoardClusterTracker();
        Map<Coords, BoardCluster> clusterMap = bct.generateClusters(getSelectedEntity(), false, true);

        for (BoardCluster cluster : clusterMap.values().stream().distinct().collect(Collectors.toList())) {
            for (Coords coords : cluster.contents.keySet()) {
                Point p = getCentreHexLocation(coords.getX(), coords.getY(), true);
                p.translate(HEX_W / 2, HEX_H / 2);
                drawHexBorder(g, p, new Color(0, 0, (20 * cluster.id) % 255), 0, 6);
            }
        }
    }

    public void clearShadowMap() {
        shadowMap = null;
    }

    /**
     * Updates the boardSize variable with the proper values for this board.
     */
    void updateBoardSize() {
        int width = (game.getBoard().getWidth() * (int) (HEX_WC * scale))
                + (int) ((HEX_W / 4) * scale);
        int height = (game.getBoard().getHeight() * (int) (HEX_H * scale))
                + (int) ((HEX_H / 2) * scale);
        boardSize = new Dimension(width, height);
    }

    /**
     * Looks through a vector of buffered images and draws them if they're
     * onscreen.
     */
    private synchronized void drawSprites(Graphics g,
            Collection<? extends Sprite> spriteArrayList) {
        for (Sprite sprite : spriteArrayList) {
            drawSprite(g, sprite);
        }
    }

    private synchronized void drawHexSpritesForHex(Coords c, Graphics g,
            Collection<? extends HexSprite> spriteArrayList) {
        Rectangle view = g.getClipBounds();

        for (HexSprite sprite : spriteArrayList) {
            Coords cp = sprite.getPosition();
            if (cp == null) {
                continue;
            }
            // This can potentially be an expensive operation
            Rectangle spriteBounds = sprite.getBounds();
            if (cp.equals(c) && view.intersects(spriteBounds) && !sprite.isHidden()) {
                if (!sprite.isReady()) {
                    sprite.prepare();
                }
                sprite.drawOnto(g, spriteBounds.x, spriteBounds.y, boardPanel, false);
            }
        }
    }

    /**
     * Draws the Entity for the given hex. This function is used by the
     * isometric rendering process so that sprites are drawn in the order that
     * hills are rendered to create the appearance that the sprite is behind the
     * hill.
     *
     * @param c               The Coordinates of the hex that the sprites should be
     *                        drawn
     *                        for.
     * @param g               The Graphics object for this board.
     * @param spriteArrayList The complete list of all IsometricSprite on the board.
     */
    private synchronized void drawIsometricSpritesForHex(Coords c, Graphics g,
            Collection<IsometricSprite> spriteArrayList) {
        Rectangle view = g.getClipBounds();
        for (IsometricSprite sprite : spriteArrayList) {
            Coords cp = sprite.getPosition();
            // This can potentially be an expensive operation
            Rectangle spriteBounds = sprite.getBounds();
            if (cp.equals(c) && view.intersects(spriteBounds) && !sprite.isHidden()) {
                if (!sprite.isReady()) {
                    sprite.prepare();
                }
                sprite.drawOnto(g, spriteBounds.x, spriteBounds.y, boardPanel, false);
            }
        }
    }

    /**
     * Draws the wrecksprites for the given hex. This function is used by the
     * isometric rendering process so that sprites are drawn in the order that
     * hills are rendered to create the appearance that the sprite is behind the
     * hill.
     *
     * @param c               The Coordinates of the hex that the sprites should be
     *                        drawn
     *                        for.
     * @param g               The Graphics object for this board.
     * @param spriteArrayList The complete list of all IsometricSprite on the board.
     */
    private synchronized void drawIsometricWreckSpritesForHex(Coords c,
            Graphics g, ArrayList<IsometricWreckSprite> spriteArrayList) {
        Rectangle view = g.getClipBounds();
        for (IsometricWreckSprite sprite : spriteArrayList) {
            Coords cp = sprite.getPosition();
            if (cp.equals(c) && view.intersects(sprite.getBounds()) && !sprite.isHidden()) {
                if (!sprite.isReady()) {
                    sprite.prepare();
                }
                sprite.drawOnto(g, sprite.getBounds().x, sprite.getBounds().y, boardPanel, false);
            }
        }
    }

    /**
     * Draws a translucent sprite without any of the companion graphics, if it
     * is in the current view. This is used only when performing isometric
     * rending. This function is used to show units (with 50% transparency) that
     * are hidden behind a hill.
     * <p>
     * TODO: Optimize this function so that it is only applied to sprites that
     * are actually hidden. This implementation performs the second rendering
     * for all sprites.
     */
    private void drawIsometricSprites(Graphics g, Collection<IsometricSprite> spriteArrayList) {
        Rectangle view = g.getClipBounds();
        for (IsometricSprite sprite : spriteArrayList) {
            // This can potentially be an expensive operation
            Rectangle spriteBounds = sprite.getBounds();
            if (view.intersects(spriteBounds) && !sprite.isHidden()) {
                if (!sprite.isReady()) {
                    sprite.prepare();
                }
                sprite.drawOnto(g, spriteBounds.x, spriteBounds.y, boardPanel, true);
            }
        }
    }

    /**
     * Draws a sprite, if it is in the current view
     */
    private void drawSprite(Graphics g, Sprite sprite) {
        Rectangle view = g.getClipBounds();
        // This can potentially be an expensive operation
        Rectangle spriteBounds = sprite.getBounds();
        if (view.intersects(spriteBounds) && !sprite.isHidden()) {
            if (!sprite.isReady()) {
                sprite.prepare();
            }
            sprite.drawOnto(g, spriteBounds.x, spriteBounds.y, boardPanel);
        }
    }

    /**
     * Draw an outline around legal deployment hexes
     */
    private void drawDeployment(Graphics g) {
        Rectangle view = g.getClipBounds();
        // only update visible hexes
        int drawX = (view.x / (int) (HEX_WC * scale)) - 1;
        int drawY = (view.y / (int) (HEX_H * scale)) - 1;

        int drawWidth = (view.width / (int) (HEX_WC * scale)) + 3;
        int drawHeight = (view.height / (int) (HEX_H * scale)) + 3;

        Board board = game.getBoard();
        boolean isAirDeployGround = en_Deployer.getMovementMode().isHover() || en_Deployer.getMovementMode().isVTOL();
        boolean isWiGE = en_Deployer.getMovementMode().isWiGE();
        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                if (en_Deployer.isAero()) {
                    if (en_Deployer.getAltitude() > 0) {
                        // Flying Aeros are always above it all
                        if (board.isLegalDeployment(c, en_Deployer) &&
                            !en_Deployer.isLocationProhibited(c, board.getMaxElevation())) {
                            drawHexBorder(g, getHexLocation(c), Color.yellow);
                        }
                    } else if (en_Deployer.getAltitude() == 0){
                        // Show prospective Altitude 1+ hexes
                        if (board.isLegalDeployment(c, en_Deployer) &&
                            !en_Deployer.isLocationProhibited(c, 1)) {
                            drawHexBorder(g, getHexLocation(c), Color.cyan);
                        }
                    }
                } else if (isAirDeployGround || isWiGE ) {
                    // Draw hexes that are legal at a higher deployment elevation
                    Hex hex = board.getHex(c);
                    // Default to Elevation 1 if ceiling + 1 <= 0.
                    int maxHeight = (isWiGE) ? 1 : (hex != null) ? Math.max(hex.ceiling() + 1, 1) : 1;
                    if (board.isLegalDeployment(c, en_Deployer) &&
                        !en_Deployer.isLocationProhibited(c, maxHeight)) {
                        drawHexBorder(g, getHexLocation(c), Color.cyan);
                    }
                }

                if (board.isLegalDeployment(c, en_Deployer) &&
                    // Draw hexes that are legal at lowest deployment elevation
                        !en_Deployer.isLocationProhibited(c)) {
                    drawHexBorder(g, getHexLocation(c), Color.yellow);
                }
            }
        }

        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                if (board.isLegalDeployment(c, en_Deployer) &&
                        !en_Deployer.isLocationProhibited(c) &&
                        en_Deployer.isLocationDeadly(c)) {
                    drawHexBorder(g, getHexLocation(c), GUIP.getWarningColor());
                }
            }
        }
    }

    /**
     * Draw indicators for the deployment zones of all players
     */
    private void drawAllDeployment(Graphics g) {
        Rectangle view = g.getClipBounds();
        // only update visible hexes
        int drawX = (view.x / (int) (HEX_WC * scale)) - 1;
        int drawY = (view.y / (int) (HEX_H * scale)) - 1;

        int drawWidth = (view.width / (int) (HEX_WC * scale)) + 3;
        int drawHeight = (view.height / (int) (HEX_H * scale)) + 3;

        java.util.List<Player> players = game.getPlayersList();
        final var gOpts = game.getOptions();

        if (gOpts.booleanOption(OptionsConstants.BASE_SET_PLAYER_DEPLOYMENT_TO_PLAYER_0)) {
            players = new ArrayList<>(
                    players.stream().filter(p -> p.isBot() || p.getId() == 0).collect(Collectors.toList()));
        }

        if (game.getPhase().isLounge() && !localPlayer.isGameMaster()
                && (gOpts.booleanOption(OptionsConstants.BASE_BLIND_DROP)
                        || gOpts.booleanOption(OptionsConstants.BASE_REAL_BLIND_DROP))) {
            players = new ArrayList<>(
                    players.stream().filter(p -> !p.isEnemyOf(localPlayer)).collect(Collectors.toList()));
        }

        Board board = game.getBoard();
        // loop through the hexes
        for (int i = 0; i < drawHeight; i++) {
            for (int j = 0; j < drawWidth; j++) {
                Coords c = new Coords(j + drawX, i + drawY);
                int pCount = 0;
                int bThickness = 1 + 10 / game.getNoOfPlayers();
                // loop through all players
                for (Player player : players) {
                    if (board.isLegalDeployment(c, player)) {
                        Color bC = player.getColour().getColour();
                        drawHexBorder(g, getHexLocation(c), bC, (bThickness + 2) * pCount, bThickness);
                        pCount++;
                    }
                }
            }
        }
    }

    /**
     * Draw a layer of a solid color (alpha possible) on the hex at Point p no
     * padding by default
     */
    void drawHexLayer(Point p, Graphics g, Color col, boolean outOfFOV) {
        drawHexLayer(p, g, col, outOfFOV, 0);
    }

    /**
     * Draw a layer of a solid color (alpha possible) on the hex at Point p with
     * some padding around the border
     */
    private void drawHexLayer(Point p, Graphics g, Color col, boolean outOfFOV,
            double pad) {
        Graphics2D g2D = (Graphics2D) g;
        g.setColor(col);

        // create stripe effect for FOV darkening but not for colored weapon
        // ranges
        int fogStripes = GUIP.getFovStripes();
        if (outOfFOV && (fogStripes > 0) && (g instanceof Graphics2D)) {
            float lineSpacing = fogStripes;
            // totally transparent here hurts the eyes
            Color c2 = new Color(col.getRed() / 2, col.getGreen() / 2,
                    col.getBlue() / 2, col.getAlpha() / 2);

            // the numbers make the lines align across hexes
            GradientPaint gp = new GradientPaint(42.0f / lineSpacing, 0.0f,
                    col, 104.0f / lineSpacing, 106.0f / lineSpacing, c2, true);
            g2D.setPaint(gp);
        }
        Composite svComposite = g2D.getComposite();
        g2D.setComposite(AlphaComposite.SrcAtop);
        g2D.fillRect(0, 0, hex_size.width, hex_size.height);
        g2D.setComposite(svComposite);
    }

    private void drawHexBorder(Graphics g, Color col, double pad, double linewidth) {
        drawHexBorder(g, new Point(0, 0), col, pad, linewidth);
    }

    public void drawHexBorder(Graphics g, Point p, Color col, double pad, double linewidth) {
        g.setColor(col);
        ((Graphics2D) g).fill(
                AffineTransform.getTranslateInstance(p.x, p.y).createTransformedShape(
                        AffineTransform.getScaleInstance(scale, scale).createTransformedShape(
                                HexDrawUtilities.getHexFullBorderArea(linewidth, pad))));
    }

    /**
     * Draw an outline around the hex at Point p no padding and a width of 1
     */
    private void drawHexBorder(Graphics g, Point p, Color col) {
        drawHexBorder(g, p, col, 0);
    }

    /**
     * Draw an outline around the hex at Point p padded around the border by pad
     * and a line-width of 1
     */
    private void drawHexBorder(Graphics g, Point p, Color col, double pad) {
        drawHexBorder(g, p, col, pad, 1);
    }

    /**
     * returns the weapon selected in the mek display, or null if none selected
     * or it is not artillery or null if the selected entity is not owned
     */
    public Mounted<?> getSelectedArtilleryWeapon() {
        // We don't want to display artillery auto-hit/adjusted fire hexes
        // during
        // the artyautohithexes phase. These could be displayed if the player
        // uses the /reset command in some situations
        if (game.getPhase().isSetArtilleryAutohitHexes()) {
            return null;
        }

        Mounted<?> selectedWeapon = selectedWeapon();

        if ((getSelectedEntity() == null) || (selectedWeapon == null)) {
            return null;
        }

        if (!getSelectedEntity().getOwner().equals(localPlayer)) {
            return null; // Not my business to see this
        }

        if (getSelectedEntity().getEquipmentNum(selectedWeapon) == -1) {
            return null; // inconsistent state - weapon not on entity
        }

        if (!((selectedWeapon.getType() instanceof WeaponType) && selectedWeapon
                .getType().hasFlag(WeaponType.F_ARTILLERY))) {
            return null; // not artillery
        }

        // otherwise, a weapon is selected, and it is artillery
        return selectedWeapon;
    }

    @Nullable
    private Mounted<?> selectedWeapon() {
        return (clientgui != null) ? clientgui.getDisplayedWeapon().orElse(null) : null;
    }

    /**
     * Draw the orbital bombardment attacks on the board view
     *
     * @author Luana Coppio
     * @param boardGraphics     The graphics object to draw on
     */
    private void drawOrbitalBombardmentHexes(Graphics boardGraphics) {
        Image orbitalBombardmentImage = tileManager.getOrbitalBombardmentImage();
        Rectangle view = boardGraphics.getClipBounds();

        // Compute the origin of the viewing area
        int drawX = (view.x / (int) (HEX_WC * scale)) - 1;
        int drawY = (view.y / (int) (HEX_H * scale)) - 1;

        // Compute size of viewing area
        int drawWidth = (view.width / (int) (HEX_WC * scale)) + 3;
        int drawHeight = (view.height / (int) (HEX_H * scale)) + 3;

        // Draw incoming artillery sprites - requires server to update client's
        // view of game
        for (Enumeration<OrbitalBombardment> attacks = game.getOrbitalBombardmentAttacks(); attacks.hasMoreElements();) {
            final OrbitalBombardment orbitalBombardment = attacks.nextElement();
            final Coords c = new Coords(orbitalBombardment.getX(), orbitalBombardment.getY());
            // Is the Coord within the viewing area?
            boolean insideViewArea = ((c.getX() >= drawX) && (c.getX() <= (drawX + drawWidth))
                && (c.getY() >= drawY) && (c.getY() <= (drawY + drawHeight)));
            if (insideViewArea) {
                Point p = getHexLocation(c);
                boardGraphics.drawImage(getScaledImage(orbitalBombardmentImage, true), p.x, p.y, boardPanel);
                for (Coords c2 : c.allAtDistanceOrLess(orbitalBombardment.getRadius())) {
                    Point p2 = getHexLocation(c2);
                    boardGraphics.drawImage(getScaledImage(orbitalBombardmentImage, true), p2.x, p2.y, boardPanel);
                }
            }

        }
    }

    /**
     * Display artillery modifier in pretargeted hexes
     */
    private void drawArtilleryHexes(Graphics g) {
        Mounted<?> weapon = getSelectedArtilleryWeapon();
        Rectangle view = g.getClipBounds();

        // Compute the origin of the viewing area
        int drawX = (view.x / (int) (HEX_WC * scale)) - 1;
        int drawY = (view.y / (int) (HEX_H * scale)) - 1;

        // Compute size of viewing area
        int drawWidth = (view.width / (int) (HEX_WC * scale)) + 3;
        int drawHeight = (view.height / (int) (HEX_H * scale)) + 3;

        Image artyIconImage;

        // Draw incoming artillery sprites - requires server to update client's
        // view of game
        for (Enumeration<ArtilleryAttackAction> attacks = game.getArtilleryAttacks(); attacks.hasMoreElements();) {
            final ArtilleryAttackAction attack = attacks.nextElement();
            final Targetable target = attack.getTarget(game);
            if (target == null) {
                continue;
            }
            final Coords c = target.getPosition();
            // Is the Coord within the viewing area?
            if ((c.getX() >= drawX) && (c.getX() <= (drawX + drawWidth))
                    && (c.getY() >= drawY) && (c.getY() <= (drawY + drawHeight))) {
                Point p = getHexLocation(c);
                artyIconImage = tileManager.getArtilleryTarget(TilesetManager.ARTILLERY_INCOMING);
                g.drawImage(getScaledImage(artyIconImage, true), p.x, p.y, boardPanel);
            }
        }

        // Draw pre-designated auto-hit hexes
        if (localPlayer != null) { // Could be null, like in map-editor
            for (Coords c : localPlayer.getArtyAutoHitHexes()) {
                // Is the Coord within the viewing area?
                if ((c.getX() >= drawX) && (c.getX() <= (drawX + drawWidth))
                        && (c.getY() >= drawY) && (c.getY() <= (drawY + drawHeight))) {

                    Point p = getHexLocation(c);
                    artyIconImage = tileManager.getArtilleryTarget(TilesetManager.ARTILLERY_AUTOHIT);
                    g.drawImage(getScaledImage(artyIconImage, true), p.x, p.y, boardPanel);
                }
            }
        }

        // Draw modifiers for selected entity and weapon
        if (weapon != null) {
            // Loop through all of the attack modifiers for this weapon
            for (ArtilleryTracker.ArtilleryModifier attackMod : getSelectedEntity().aTracker
                    .getWeaponModifiers(weapon)) {
                Coords c = attackMod.getCoords();
                // Is the Coord within the viewing area?
                if ((c.getX() >= drawX) && (c.getX() <= (drawX + drawWidth))
                        && (c.getY() >= drawY) && (c.getY() <= (drawY + drawHeight))) {

                    Point p = getHexLocation(c);
                    // draw the crosshairs
                    if (attackMod.getModifier() == TargetRoll.AUTOMATIC_SUCCESS) {
                        // predesignated or already hit
                        artyIconImage = tileManager
                                .getArtilleryTarget(TilesetManager.ARTILLERY_AUTOHIT);
                    } else {
                        artyIconImage = tileManager
                                .getArtilleryTarget(TilesetManager.ARTILLERY_ADJUSTED);
                    }
                    g.drawImage(getScaledImage(artyIconImage, true), p.x, p.y, boardPanel);
                }
            }
        }
    }

    /**
     * Writes "MINEFIELD" in minefield hexes...
     */
    private void drawMinefields(Graphics g) {
        Rectangle view = g.getClipBounds();
        // only update visible hexes
        int drawX = (view.x / (int) (HEX_WC * scale)) - 1;
        int drawY = (view.y / (int) (HEX_H * scale)) - 1;

        int drawWidth = (view.width / (int) (HEX_WC * scale)) + 3;
        int drawHeight = (view.height / (int) (HEX_H * scale)) + 3;

        int maxX = drawX + drawWidth;
        int maxY = drawY + drawHeight;

        Board board = game.getBoard();
        for (Enumeration<Coords> minedCoords = game.getMinedCoords(); minedCoords.hasMoreElements();) {
            Coords c = minedCoords.nextElement();
            // If the coords aren't visible, skip
            if ((c.getX() < drawX) || (c.getX() > maxX) || (c.getY() < drawY) || (c.getY() > maxY)
                    || !board.contains(c)) {
                continue;
            }

            Point p = getHexLocation(c);
            Image mineImg = getScaledImage(tileManager.getMinefieldSign(), true);
            g.drawImage(mineImg, p.x + (int) (13 * scale), p.y + (int) (13 * scale), boardPanel);

            g.setColor(Color.black);
            int nbrMfs = game.getNbrMinefields(c);
            if (nbrMfs > 1) {
                drawCenteredString(Messages.getString("BoardView1.Multiple"),
                        p.x, p.y + (int) (51 * scale), font_minefield, g);
            } else if (nbrMfs == 1) {
                Minefield mf = game.getMinefields(c).get(0);

                switch (mf.getType()) {
                    case Minefield.TYPE_CONVENTIONAL:
                        drawCenteredString(Messages.getString("BoardView1.Conventional") + mf.getDensity() + ")",
                                p.x, p.y + (int) (51 * scale), font_minefield, g);
                        break;
                    case Minefield.TYPE_INFERNO:
                        drawCenteredString(Messages.getString("BoardView1.Inferno") + mf.getDensity() + ")",
                                p.x, p.y + (int) (51 * scale), font_minefield, g);
                        break;
                    case Minefield.TYPE_ACTIVE:
                        drawCenteredString(Messages.getString("BoardView1.Active") + mf.getDensity() + ")",
                                p.x, p.y + (int) (51 * scale), font_minefield, g);
                        break;
                    case Minefield.TYPE_COMMAND_DETONATED:
                        drawCenteredString(Messages.getString("BoardView1.Command-"),
                                p.x, p.y + (int) (51 * scale), font_minefield, g);
                        drawCenteredString(Messages.getString("BoardView1.detonated" + mf.getDensity() + ")"),
                                p.x, p.y + (int) (60 * scale), font_minefield, g);
                        break;
                    case Minefield.TYPE_VIBRABOMB:
                        drawCenteredString(Messages.getString("BoardView1.Vibrabomb"),
                                p.x, p.y + (int) (51 * scale), font_minefield, g);
                        if (mf.getPlayerId() == localPlayer.getId()) {
                            drawCenteredString("(" + mf.getSetting() + ")",
                                    p.x, p.y + (int) (60 * scale), font_minefield, g);
                        }
                        break;
                }
            }
        }
    }

    private void drawCenteredString(String string, int x, int y, Font font, Graphics graph) {
        FontMetrics currentMetrics = boardPanel.getFontMetrics(font);
        int stringWidth = currentMetrics.stringWidth(string);
        x += ((hex_size.width - stringWidth) / 2);
        graph.setFont(font);
        graph.drawString(string, x, y);
    }

    @Override
    public BufferedImage getEntireBoardImage(boolean ignoreUnits, boolean useBaseZoom) {
        // Set zoom to base, so we get a consistent board image
        int oldZoom = zoomIndex;
        if (useBaseZoom) {
            zoomIndex = BASE_ZOOM_INDEX;
            zoom();
        }

        Image entireBoard = boardPanel.createImage(boardSize.width, boardSize.height);
        Graphics2D boardGraph = (Graphics2D) entireBoard.getGraphics();
        boardGraph.setClip(0, 0, boardSize.width, boardSize.height);
        UIUtil.setHighQualityRendering(boardGraph);

        if (shadowMap == null) {
            shadowMap = shadowHelper.updateShadowMap();
        }

        // Draw hexes
        drawHexes(boardGraph, new Rectangle(boardSize), ignoreUnits);

        // If we aren't ignoring units, draw everything else
        if (!ignoreUnits) {
            // draw wrecks
            if (GUIP.getShowWrecks() && !useIsometric()) {
                drawSprites(boardGraph, wreckSprites);
            }

            // Minefield signs all over the place!
            drawMinefields(boardGraph);

            // Artillery targets
            drawArtilleryHexes(boardGraph);

            // draw Orbital Bombardment targets;
            drawOrbitalBombardmentHexes(boardGraph);

            // draw highlight border
            drawSprite(boardGraph, highlightSprite);

            // draw cursors
            drawSprite(boardGraph, cursorSprite);
            drawSprite(boardGraph, selectedSprite);
            drawSprite(boardGraph, firstLOSSprite);
            drawSprite(boardGraph, secondLOSSprite);

            // draw deployment indicators.
            // For Isometric rendering, this is done during drawHexes
            if ((en_Deployer != null) && !useIsometric()) {
                drawDeployment(boardGraph);
            }

            if (game.getPhase().isSetArtilleryAutohitHexes() && showAllDeployment) {
                drawAllDeployment(boardGraph);
            }

            // draw C3 links
            drawSprites(boardGraph, c3Sprites);

            // draw flyover routes
            if (game.getBoard().onGround()) {
                drawSprites(boardGraph, vtolAttackSprites);
                drawSprites(boardGraph, flyOverSprites);
            }

            // draw moving onscreen entities
            drawSprites(boardGraph, movingEntitySprites);

            // draw ghost onscreen entities
            drawSprites(boardGraph, ghostEntitySprites);

            // draw onscreen attacks
            drawSprites(boardGraph, attackSprites);

            // draw movement vectors.
            if (game.getPhase().isMovement() && game.useVectorMove()) {
                drawSprites(boardGraph, movementSprites);
            }

            // draw movement, if valid
            drawSprites(boardGraph, pathSprites);

            // draw flight path indicators
            drawSprites(boardGraph, fpiSprites);

            if (game.getPhase().isFiring()) {
                for (Coords c : strafingCoords) {
                    drawHexBorder(boardGraph, getHexLocation(c), Color.yellow, 0, 3);
                }
            }

            if (!useIsometric()) {
                // In non-iso mode, all sprites can now be drawn according to their internal
                // priority (draw order)
                drawSprites(boardGraph, allSprites);
            } else {
                // In iso mode, some sprites are drawn in drawHexes so they can go behind
                // terrain; draw only the others here
                drawSprites(boardGraph, overTerrainSprites);
            }
        }
        boardGraph.dispose();

        // Restore the zoom setting
        zoomIndex = oldZoom;
        zoom();

        return (BufferedImage) entireBoard;
    }

    private void drawHexes(Graphics g, Rectangle view) {
        drawHexes(g, view, false);
    }

    /**
     * Redraws all hexes in the specified rectangle
     */
    private void drawHexes(Graphics g, Rectangle view, boolean saveBoardImage) {
        // only update visible hexes
        double xs = (int) (HEX_WC * scale);
        double ys = (int) (HEX_H * scale);

        int drawX = (int) (view.x / xs) - 1;
        int drawY = (int) (view.y / ys) - 1;

        int drawWidth = (int) (view.width / xs) + 3;
        int drawHeight = (int) (view.height / ys) + 3;

        // draw some hexes.
        if (useIsometric()) {
            Board board = game.getBoard();
            for (int y = 0; y < drawHeight; y++) {
                // Half of each row is one-half hex
                // farther back (above) the other; draw those first
                for (int s = 0; s <= 1; s++) {
                    for (int x = s; x < drawWidth + s + 1; x = x + 2) {
                        // For s == 0 the x coordinate MUST be an even number
                        // to get correct occlusion; drawX may be any int though
                        Coords c = new Coords(x + drawX / 2 * 2, y + drawY);
                        Hex hex = board.getHex(c);
                        if ((hex != null)) {
                            drawHex(c, g, saveBoardImage);
                            drawOrthograph(c, g);
                            drawHexSpritesForHex(c, g, behindTerrainHexSprites);
                            if ((en_Deployer != null)
                                    && board.isLegalDeployment(c, en_Deployer)) {
                                drawHexBorder(g, getHexLocation(c), Color.YELLOW);
                            }
                            drawOrthograph(c, g);
                        }
                    }
                }

                for (int x = 0; x < drawWidth; x++) {
                    Coords c = new Coords(x + drawX, y + drawY);
                    Hex hex = board.getHex(c);
                    if (hex != null) {
                        if (!saveBoardImage) {
                            if (GUIP.getShowWrecks()) {
                                drawIsometricWreckSpritesForHex(c, g, isometricWreckSprites);
                            }
                        }
                    }
                }
            }
            if (!saveBoardImage) {
                // If we are using Isometric rendering, redraw the entity sprites at 50%
                // transparent
                // so sprites hidden behind hills can still be seen by the user.
                drawIsometricSprites(g, isometricSprites);
            }
        } else {
            // Draw hexes without regard to elevation when not using Isometric, since it
            // does not
            // matter.
            for (int i = 0; i < drawHeight; i++) {
                for (int j = 0; j < drawWidth; j++) {
                    Coords c = new Coords(j + drawX, i + drawY);
                    drawHex(c, g, saveBoardImage);
                }
            }
        }
    }

    /**
     * Draws a hex onto the board buffer. This assumes that drawRect is current, and
     * does not check
     * if the hex is visible.
     */
    private void drawHex(Coords c, Graphics boardGraph, boolean saveBoardImage) {
        if (!game.getBoard().contains(c)) {
            return;
        }

        final Hex hex = game.getBoard().getHex(c);
        if (hex == null) {
            return;
        }
        final Point hexLoc = getHexLocation(c);
        PlanetaryConditions conditions = game.getPlanetaryConditions();

        // Check the cache to see if we already have the image
        HexImageCacheEntry cacheEntry = hexImageCache.get(c);
        if ((cacheEntry != null) && !cacheEntry.needsUpdating) {
            boardGraph.drawImage(cacheEntry.hexImage, hexLoc.x, hexLoc.y, boardPanel);
            return;
        }

        int level = hex.getLevel();
        int depth = hex.depth(false);

        Terrain basement = hex.getTerrain(Terrains.BLDG_BASEMENT_TYPE);
        if (basement != null) {
            depth = 0;
        }

        int height = Math.max(hex.terrainLevel(Terrains.BLDG_ELEV), hex.terrainLevel(Terrains.BRIDGE_ELEV));
        height = Math.max(height, hex.terrainLevel(Terrains.INDUSTRIAL));

        // get the base tile image
        Image baseImage = tileManager.baseFor(hex);
        Image scaledImage = getScaledImage(baseImage, true);

        // Some hex images shouldn't be cached, like if they are animated
        boolean dontCache = animatedImages.contains(baseImage.hashCode());

        // check if this is a standard tile image 84x72 or something different
        boolean standardTile = (baseImage.getHeight(null) == HEX_H)
                && (baseImage.getWidth(null) == HEX_W);

        int imgWidth = scaledImage.getWidth(null);
        int imgHeight = scaledImage.getHeight(null);

        // do not make larger than hex images even when the input image is big
        int origImgWidth = imgWidth; // save for later, needed for large tiles
        int origImgHeight = imgHeight;

        imgWidth = Math.min(imgWidth, (int) (HEX_W * scale));
        imgHeight = Math.min(imgHeight, (int) (HEX_H * scale));

        if (useIsometric()) {
            int largestLevelDiff = 0;
            for (int dir : allDirections) {
                Hex adjHex = game.getBoard().getHexInDir(c, dir);
                if (adjHex == null) {
                    continue;
                }
                int levelDiff = Math.abs(level - adjHex.getLevel());
                if (levelDiff > largestLevelDiff) {
                    largestLevelDiff = levelDiff;
                }
            }
            imgHeight += (int) (HEX_ELEV * scale * largestLevelDiff);
        }
        // If the base image isn't ready, we should signal a repaint and stop
        if ((imgWidth < 0) || (imgHeight < 0)) {
            boardPanel.repaint();
            return;
        }

        BufferedImage hexImage = new BufferedImage(imgWidth, imgHeight,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = (Graphics2D) (hexImage.getGraphics());
        UIUtil.setHighQualityRendering(g);

        if (standardTile) { // is the image hex-sized, 84*72?
            g.drawImage(scaledImage, 0, 0, boardPanel);
        } else { // Draw image for a texture larger than a hex
            Point p1SRC = getHexLocationLargeTile(c.getX(), c.getY());
            p1SRC.x = p1SRC.x % origImgWidth;
            p1SRC.y = p1SRC.y % origImgHeight;
            Point p2SRC = new Point((int) (p1SRC.x + HEX_W * scale),
                    (int) (p1SRC.y + HEX_H * scale));
            Point p2DST = new Point((int) (HEX_W * scale),
                    (int) (HEX_H * scale));

            // hex mask to limit drawing to the hex shape
            // TODO : this is not ideal yet but at least it draws without leaving gaps at
            // any zoom
            Image hexMask = getScaledImage(tileManager.getHexMask(), true);
            g.drawImage(hexMask, 0, 0, boardPanel);
            Composite svComp = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1f));

            // paint the right slice from the big pic
            g.drawImage(scaledImage, 0, 0, p2DST.x, p2DST.y, p1SRC.x, p1SRC.y,
                    p2SRC.x, p2SRC.y, null);

            // Handle wrapping of the image
            if (p2SRC.x > origImgWidth && p2SRC.y <= origImgHeight) {
                g.drawImage(scaledImage, origImgWidth - p1SRC.x, 0, p2DST.x,
                        p2DST.y, 0, p1SRC.y, p2SRC.x - origImgWidth, p2SRC.y,
                        null); // paint addtl slice on the left side
            } else if (p2SRC.x <= origImgWidth && p2SRC.y > origImgHeight) {
                g.drawImage(scaledImage, 0, origImgHeight - p1SRC.y, p2DST.x,
                        p2DST.y, p1SRC.x, 0, p2SRC.x, p2SRC.y - origImgHeight,
                        null); // paint addtl slice on the top
            } else if (p2SRC.x > origImgWidth && p2SRC.y > origImgHeight) {
                g.drawImage(scaledImage, origImgWidth - p1SRC.x, 0, p2DST.x,
                        p2DST.y, 0, p1SRC.y, p2SRC.x - origImgWidth, p2SRC.y,
                        null); // paint addtl slice on the top
                g.drawImage(scaledImage, 0, origImgHeight - p1SRC.y, p2DST.x,
                        p2DST.y, p1SRC.x, 0, p2SRC.x, p2SRC.y - origImgHeight,
                        null); // paint addtl slice on the left side
                // paint addtl slice on the top left side
                g.drawImage(scaledImage, origImgWidth - p1SRC.x, origImgHeight
                        - p1SRC.y, p2DST.x, p2DST.y, 0, 0,
                        p2SRC.x
                                - origImgWidth,
                        p2SRC.y - origImgHeight, null);
            }

            g.setComposite(svComp);
        }

        // To place roads under the shadow map, some supers
        // have to be drawn before the shadow map, otherwise the supers are
        // drawn after. Unfortunately the supers images
        // themselves can't be checked for roads.
        java.util.List<Image> supers = tileManager.supersFor(hex);
        boolean supersUnderShadow = false;
        if (hex.containsTerrain(Terrains.ROAD)
                || hex.containsTerrain(Terrains.WATER)
                || hex.containsTerrain(Terrains.PAVEMENT)
                || hex.containsTerrain(Terrains.GROUND_FLUFF)
                || hex.containsTerrain(Terrains.ROUGH)
                || hex.containsTerrain(Terrains.RUBBLE)
                || hex.containsTerrain(Terrains.SNOW)) {
            supersUnderShadow = true;
            if (supers != null) {
                for (Image image : supers) {
                    if (animatedImages.contains(image.hashCode())) {
                        dontCache = true;
                    }
                    scaledImage = getScaledImage(image, true);
                    g.drawImage(scaledImage, 0, 0, boardPanel);
                }
            }
        }

        // Add the terrain & building shadows
        if (GUIP.getShadowMap() && (shadowMap != null)) {
            Point p1SRC = getHexLocationLargeTile(c.getX(), c.getY(), 1);
            Point p2SRC = new Point(p1SRC.x + HEX_W, p1SRC.y + HEX_H);
            Point p2DST = new Point(hex_size.width, hex_size.height);

            Composite svComp = g.getComposite();
            if (conditions.getLight().isDay()) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.55f));
            } else {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.45f));
            }

            // paint the right slice from the big pic
            g.drawImage(shadowMap, 0, 0, p2DST.x, p2DST.y, p1SRC.x, p1SRC.y, p2SRC.x, p2SRC.y, null);
            g.setComposite(svComp);
        }

        if (!supersUnderShadow) {
            if (supers != null) {
                for (Image image : supers) {
                    if (null != image) {
                        if (animatedImages.contains(image.hashCode())) {
                            dontCache = true;
                        }
                        scaledImage = getScaledImage(image, true);
                        g.drawImage(scaledImage, 0, 0, boardPanel);
                    }
                }
            }
        }

        // Check for buildings and woods burried under their own shadows.
        if ((supers != null) && supersUnderShadow
                && (hex.containsTerrain(Terrains.BUILDING) || hex.containsTerrain(Terrains.WOODS))) {
            Image lastSuper = supers.get(supers.size() - 1);
            scaledImage = getScaledImage(lastSuper, true);
            g.drawImage(scaledImage, 0, 0, boardPanel);
        }

        // AO Hex Shadow in this hex when a higher one is adjacent
        if (GUIP.getAOHexShadows()) {
            for (int dir : allDirections) {
                Shape ShadowShape = getElevationShadowArea(c, dir);
                GradientPaint gpl = getElevationShadowGP(c, dir);
                if ((ShadowShape != null) && (gpl != null)) {
                    g.setPaint(gpl);
                    g.fill(getElevationShadowArea(c, dir));
                }
            }
        }

        // Orthos = bridges
        java.util.List<Image> orthos = tileManager.orthoFor(hex);
        if (orthos != null) {
            for (Image image : orthos) {
                if (animatedImages.contains(image.hashCode())) {
                    dontCache = true;
                }
                scaledImage = getScaledImage(image, true);
                if (!useIsometric()) {
                    g.drawImage(scaledImage, 0, 0, boardPanel);
                }
            }
        }

        // Shade and add static noise to hexes that are in an ECM field
        if (ecmHexes != null) {
            Color tint = ecmHexes.get(c);
            if (tint != null) {
                Color origColor = g.getColor();
                g.setColor(tint);
                AffineTransform sc = new AffineTransform();
                sc.scale(scale, scale);
                g.fill(sc.createTransformedShape(HEX_POLY));
                g.setColor(origColor);
                Image staticImage = getScaledImage(tileManager.getEcmStaticImage(tint), false);
                g.drawImage(staticImage, 0, 0, staticImage.getWidth(null),
                        staticImage.getHeight(null), boardPanel);
            }
        }
        // Shade hexes that are in an ECCM field
        if (eccmHexes != null) {
            Color tint = eccmHexes.get(c);
            if (tint != null) {
                Color origColor = g.getColor();
                g.setColor(tint);
                AffineTransform sc = new AffineTransform();
                sc.scale(scale, scale);
                g.fill(sc.createTransformedShape(HEX_POLY));
                g.setColor(origColor);
            }
        }
        // Highlight hexes that contain the source of an ECM field
        if (ecmCenters != null) {
            Color tint = ecmCenters.get(c);
            if (tint != null) {
                drawHexBorder(g, tint.darker(), 5, 10);
            }
        }

        // Highlight hexes that contain the source of an ECCM field
        if (eccmCenters != null) {
            Color tint = eccmCenters.get(c);
            if (tint != null) {
                drawHexBorder(g, tint.darker(), 5, 10);
            }
        }

        // Darken the hex for nighttime, if applicable
        if (GUIP.getDarkenMapAtNight()
                && IlluminationLevel.determineIlluminationLevel(game, c).isNone()
                && conditions.getLight().isDuskOrFullMoonOrMoonlessOrPitchBack()) {
            for (int x = 0; x < hexImage.getWidth(); ++x) {
                for (int y = 0; y < hexImage.getHeight(); ++y) {
                    hexImage.setRGB(x, y, getNightDarkenedColor(hexImage.getRGB(x, y)));
                }
            }
        }

        if (hex.containsTerrain(Terrains.DEPLOYMENT_ZONE) && (game.getPhase().isUnknown())) {
            drawHexBorder(g, Color.yellow, 5, 5);
            drawCenteredString("DZ " + Board.exitsAsIntList(hex.getTerrain(Terrains.DEPLOYMENT_ZONE).getExits()),
                    0, (int) (50 * scale), font_note, g);
        }

        // Set the text color according to Preferences or Light Gray in space
        g.setColor(GUIP.getBoardTextColor());
        if (game.getBoard().inSpace()) {
            g.setColor(GUIP.getBoardSpaceTextColor());
        }

        // draw special stuff for the hex
        final Collection<SpecialHexDisplay> shdList = game.getBoard().getSpecialHexDisplay(c);
        try {
            if (shdList != null) {
                for (SpecialHexDisplay shd : shdList) {
                    if (shd.drawNow(game.getPhase(), game.getRoundCount(), localPlayer, GUIP)) {
                        scaledImage = getScaledImage(shd.getDefaultImage(), true);
                        g.drawImage(scaledImage, 0, 0, boardPanel);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e, "Exception, probably can't load file.");
            drawCenteredString("Loading Error", 0, (int) (50 * scale), font_note, g);
            return;
        }

        // write hex coordinate unless deactivated or scale factor too small
        if (GUIP.getCoordsEnabled() && (scale >= 0.5)) {
            drawCenteredString(c.getBoardNum(), 0, (int) (12 * scale), font_hexnum, g);
        }

        if (displayInvalidHexInfo && !hex.isValid(null)) {
            Point hexCenter = new Point((int) (HEX_W / 2 * scale), (int) (HEX_H / 2 * scale));
            invalidString.at(hexCenter).fontSize(14.0f * scale).outline(Color.WHITE, scale / 2).draw(g);
        }

        // write terrain level / water depth / building height
        if (scale > 0.5f) {
            int ypos = HEX_H - 2;
            if (level != 0) {
                drawCenteredString(Messages.getString("BoardView1.LEVEL") + level,
                        0, (int) (ypos * scale), font_elev, g);
                ypos -= 10;
            }
            if (depth != 0) {
                drawCenteredString(Messages.getString("BoardView1.DEPTH") + depth,
                        0, (int) (ypos * scale), font_elev, g);
                ypos -= 10;
            }
            if (height > 0) {
                g.setColor(GUIP.getBuildingTextColor());
                drawCenteredString(Messages.getString("BoardView1.HEIGHT") + height,
                        0, (int) (ypos * scale), font_elev, g);
                ypos -= 10;
            }
            if (hex.terrainLevel(Terrains.FOLIAGE_ELEV) == 1) {
                g.setColor(GUIP.getLowFoliageColor());
                drawCenteredString(Messages.getString("BoardView1.LowFoliage"),
                        0, (int) (ypos * scale), font_elev, g);
                ypos -= 10;
            }
        }

        // Used to make the following draw calls shorter
        int s21 = (int) (21 * scale);
        int s71 = (int) (71 * scale);
        int s35 = (int) (35 * scale);
        int s36 = (int) (36 * scale);
        int s62 = (int) (62 * scale);
        int s83 = (int) (83 * scale);

        Point p1 = new Point(s62, 0);
        Point p2 = new Point(s21, 0);
        Point p3 = new Point(s83, s35);
        Point p4 = new Point(s83, s36);
        Point p5 = new Point(s62, s71);
        Point p6 = new Point(s21, s71);
        Point p7 = new Point(0, s36);
        Point p8 = new Point(0, s35);

        g.setColor(Color.black);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // draw elevation borders
        if (drawElevationLine(c, 0)) {
            drawIsometricElevation(c, Color.GRAY, p1, p2, 0, g);
            if (GUIP.getLevelHighlight()) {
                g.drawLine(s21, 0, s62, 0);
            }
        }

        if (drawElevationLine(c, 1)) {
            drawIsometricElevation(c, Color.DARK_GRAY, p3, p1, 1, g);
            if (GUIP.getLevelHighlight()) {
                g.drawLine(s62, 0, s83, s35);
            }
        }

        if (drawElevationLine(c, 2)) {
            drawIsometricElevation(c, Color.LIGHT_GRAY, p4, p5, 2, g);
            if (GUIP.getLevelHighlight()) {
                g.drawLine(s83, s36, s62, s71);
            }
        }

        if (drawElevationLine(c, 3)) {
            drawIsometricElevation(c, Color.GRAY, p6, p5, 3, g);
            if (GUIP.getLevelHighlight()) {
                g.drawLine(s62, s71, s21, s71);
            }
        }

        if (drawElevationLine(c, 4)) {
            drawIsometricElevation(c, Color.DARK_GRAY, p7, p6, 4, g);
            if (GUIP.getLevelHighlight()) {
                g.drawLine(s21, s71, 0, s36);
            }
        }

        if (drawElevationLine(c, 5)) {
            drawIsometricElevation(c, Color.LIGHT_GRAY, p8, p2, 5, g);
            if (GUIP.getLevelHighlight()) {
                g.drawLine(0, s35, s21, 0);
            }

        }

        boolean hasLoS = fovHighlightingAndDarkening.draw(g, c, 0, 0, saveBoardImage);

        // draw mapsheet borders
        if (GUIP.getShowMapsheets()) {
            g.setColor(GUIP.getMapsheetColor());
            if ((c.getX() % 16) == 0) {
                // left edge of sheet (edge 4 & 5)
                g.drawLine(s21, s71, 0, s36);
                g.drawLine(0, s35, s21, 0);
            } else if ((c.getX() % 16) == 15) {
                // right edge of sheet (edge 1 & 2)
                g.drawLine(s62, 0, s83, s35);
                g.drawLine(s83, s36, s62, s71);
            }

            if ((c.getY() % 17) == 0) {
                // top edge of sheet (edge 0 and possible 1 & 5)
                g.drawLine(s21, 0, s62, 0);
                if ((c.getX() % 2) == 0) {
                    g.drawLine(s62, 0, s83, s35);
                    g.drawLine(0, s35, s21, 0);
                }
            } else if ((c.getY() % 17) == 16) {
                // bottom edge of sheet (edge 3 and possible 2 & 4)
                g.drawLine(s62, s71, s21, s71);
                if ((c.getX() % 2) == 1) {
                    g.drawLine(s83, s36, s62, s71);
                    g.drawLine(s21, s71, 0, s36);
                }
            }
        }

        if (!hasLoS && GUIP.getFovGrayscale()) {
            // rework the pixels to grayscale
            for (int x = 0; x < hexImage.getWidth(); x++) {
                for (int y = 0; y < hexImage.getHeight(); y++) {
                    int rgb = hexImage.getRGB(x, y);
                    int rd = (rgb >> 16) & 0xFF;
                    int gr = (rgb >> 8) & 0xFF;
                    int bl = (rgb & 0xFF);
                    int al = (rgb >> 24);

                    int grayLevel = (rd + gr + bl) / 3;
                    int gray = (al << 24) + (grayLevel << 16) + (grayLevel << 8) + grayLevel;
                    hexImage.setRGB(x, y, gray);
                }
            }
        }

        cacheEntry = new HexImageCacheEntry(hexImage);
        if (!dontCache) {
            hexImageCache.put(c, cacheEntry);
        }
        boardGraph.drawImage(cacheEntry.hexImage, hexLoc.x, hexLoc.y, boardPanel);
    }

    /**
     * Draws a orthographic hex onto the board buffer. This assumes that drawRect is
     * current, and
     * does not check if the hex is visible.
     */
    private void drawOrthograph(Coords c, Graphics boardGraph) {
        if (!game.getBoard().contains(c)) {
            return;
        }

        final Hex oHex = game.getBoard().getHex(c);
        final Point oHexLoc = getHexLocation(c);
        // Adjust the draw height for bridges according to their elevation
        int elevOffset = oHex.terrainLevel(Terrains.BRIDGE_ELEV);

        int orthX = oHexLoc.x;
        int orthY = oHexLoc.y - (int) (HEX_ELEV * scale * elevOffset);
        if (!useIsometric()) {
            orthY = oHexLoc.y;
        }
        if (tileManager.orthoFor(oHex) != null) {
            for (Image image : tileManager.orthoFor(oHex)) {
                BufferedImage scaledImage = ImageUtil.createAcceleratedImage(getScaledImage(image, true));

                // Darken the hex for nighttime, if applicable
                PlanetaryConditions conditions = game.getPlanetaryConditions();
                if (GUIP.getDarkenMapAtNight()
                        && IlluminationLevel.determineIlluminationLevel(game, c).isNone()
                        && conditions.getLight().isDuskOrFullMoonOrMoonlessOrPitchBack()) {
                    for (int x = 0; x < scaledImage.getWidth(null); ++x) {
                        for (int y = 0; y < scaledImage.getHeight(); ++y) {
                            scaledImage.setRGB(x, y, getNightDarkenedColor(scaledImage.getRGB(x, y)));
                        }
                    }
                }

                // draw orthogonal
                boardGraph.drawImage(scaledImage, orthX, orthY, boardPanel);
            }
        }
    }

    public boolean useIsometric() {
        return drawIsometric;
    }

    /**
     * Draws the Isometric elevation for the hex at the given coordinates (c) on
     * the side indicated by the direction (dir). This method only draws a
     * triangle for the elevation, the companion triangle representing the
     * adjacent hex is also needed. The two triangles when drawn together make a
     * complete rectangle representing the complete elevated hex side.
     * <p>
     * By drawing the elevated hex as two separate triangles we avoid clipping
     * problems with other hexes because the lower elevation is rendered before
     * the higher elevation. Thus, any hexes that have a higher elevation than
     * the lower hex will overwrite the lower hex.
     * <p>
     * The Triangle for each hex side is formed by points p1, p2, and p3. Where
     * p1 and p2 are the original hex edges, and p3 has the same X value as p1,
     * but the y value has been increased (or decreased) based on the difference
     * in elevation between the given hex and the adjacent hex.
     *
     * @param c     Coordinates of the source hex.
     * @param color Color to use for the elevation polygons.
     * @param p1    The First point on the edge of the hex.
     * @param p2    The second point on the edge of the hex.
     * @param dir   The side of the hex to have the elevation drawn on.
     * @param g
     */
    private final void drawIsometricElevation(Coords c, Color color, Point p1, Point p2, int dir, Graphics g) {
        final Hex dest = game.getBoard().getHexInDir(c, dir);
        final Hex src = game.getBoard().getHex(c);

        if (!useIsometric() || GUIP.getFloatingIso()) {
            return;
        }

        // Pad polygon size slightly to avoid rounding errors from scale float.
        int fudge = -1;
        if ((dir == 2) || (dir == 4) || (dir == 3)) {
            fudge = 1;
        }

        final int elev = src.getLevel();
        // If the Destination is null, draw the complete elevation side.
        if ((dest == null) && (elev > 0)
                && ((dir == 2) || (dir == 3) || (dir == 4))) {

            // Determine the depth of the edge that needs to be drawn.
            int height = elev;
            Hex southHex = game.getBoard().getHexInDir(c, 3);
            if ((dir != 3) && (southHex != null) && (elev > southHex.getLevel())) {
                height = elev - southHex.getLevel();
            }
            int scaledHeight = (int) (HEX_ELEV * scale * height);

            Polygon p = new Polygon(new int[] { p1.x, p2.x, p2.x, p1.x },
                    new int[] { p1.y + fudge, p2.y + fudge,
                            p2.y + scaledHeight, p1.y + scaledHeight },
                    4);
            g.setColor(color);
            g.drawPolygon(p);
            g.fillPolygon(p);

            g.setColor(Color.BLACK);
            if ((dir == 2) || (dir == 4)) {
                g.drawLine(p1.x, p1.y, p1.x, p1.y + scaledHeight);
            }
            return;
        } else if (dest == null) {
            return;
        }

        int delta = elev - dest.getLevel();
        // Don't draw the elevation if there is no exposed edge for the player
        // to see.
        if ((delta == 0)
                || (((dir == 0) || (dir == 1) || (dir == 5)) && (delta > 0))
                || (((dir == 2) || (dir == 3) || (dir == 4)) && (delta < 0))) {
            return;
        }

        if ((dir == 2) || (dir == 3) || (dir == 4)) {
            int scaledDelta = (int) (HEX_ELEV * scale * delta);
            Point p3 = new Point(p1.x, p1.y + scaledDelta + fudge);

            Polygon p = new Polygon(new int[] { p1.x, p2.x, p2.x, p1.x },
                    new int[] { p1.y + fudge, p2.y + fudge,
                            p2.y + fudge + scaledDelta,
                            p1.y + fudge + scaledDelta },
                    4);

            if ((p1.y + fudge) < 0) {
                logger.info("Negative Y value (Fudge)!: " + (p1.y + fudge));
            }

            if ((p2.y + fudge) < 0) {
                logger.info("Negative Y value (Fudge)!: " + (p2.y + fudge));
            }

            if ((p2.y + fudge + scaledDelta) < 0) {
                logger.info("Negative Y value!: " + (p2.y + fudge + scaledDelta));
            }

            if ((p1.y + fudge + scaledDelta) < 0) {
                logger.info("Negative Y value!: " + (p1.y + fudge + scaledDelta));
            }
            g.setColor(color);
            g.drawPolygon(p);
            g.fillPolygon(p);

            g.setColor(Color.BLACK);
            if ((dir == 1) || (dir == 2) || (dir == 5) || (dir == 4)) {
                g.drawLine(p1.x, p1.y, p3.x, p3.y);
            }
        }
    }

    /**
     * Returns true if an elevation line should be drawn between the starting
     * hex and the hex in the direction specified. Results should be transitive,
     * that is, if a line is drawn in one direction, it should be drawn in the
     * opposite direction as well.
     */
    private boolean drawElevationLine(Coords src, int direction) {
        final Hex srcHex = game.getBoard().getHex(src);
        final Hex destHex = game.getBoard().getHexInDir(src, direction);
        if ((destHex == null) && (srcHex.getLevel() != 0)) {
            return true;
        } else if (destHex == null) {
            return false;
        } else if (srcHex.getLevel() != destHex.getLevel()) {
            return true;
        } else {
            return (srcHex.floor() != destHex.floor());
        }
    }

    /**
     * Given an int-packed RGB value, apply a modifier for the light level and
     * return the result.
     *
     * @param rgb int-packed ARGB value.
     * @return An int-packed ARGB value, which is an adjusted value of the input,
     *         based on the light level
     */
    public int getNightDarkenedColor(int rgb) {
        int rd = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        int al = (rgb >> 24);

        switch (game.getPlanetaryConditions().getLight()) {
            case FULL_MOON:
            case MOONLESS:
                rd = rd / 4; // 1/4 red
                gr = gr / 4; // 1/4 green
                bl = bl / 2; // half blue
                break;
            case PITCH_BLACK:
                int gy = (rd + gr + bl) / 16;
                if (Math.random() < 0.3) {
                    gy = gy * 4 / 5;
                }
                if (Math.random() < 0.3) {
                    gy = gy * 5 / 4;
                }
                rd = gy + rd / 5;
                gr = gy + gr / 5;
                bl = gy + bl / 5;
                break;
            case DUSK:
                bl = bl * 3 / 4;
                break;
            default:
                break;
        }

        return (al << 24) + (rd << 16) + (gr << 8) + bl;
    }

    /**
     * Generates a Shape drawing area for the hex shadow effect in a lower hex
     * when a higher hex is found in direction.
     */
    private @Nullable Shape getElevationShadowArea(Coords src, int direction) {
        final Hex srcHex = game.getBoard().getHex(src);
        final Hex destHex = game.getBoard().getHexInDir(src, direction);

        // When at the board edge, create a shadow in hexes of level < 0
        if (destHex == null) {
            if (srcHex.getLevel() >= 0) {
                return null;
            }
        } else {
            // no shadow area when the current hex is not lower than the next hex in
            // direction
            if (srcHex.getLevel() >= destHex.getLevel()) {
                return null;
            } else if (GUIP.getHexInclines()
                    && (destHex.getLevel() - srcHex.getLevel() < 2)
                    && !destHex.hasCliffTopTowards(srcHex)) {
                return null;
            }
        }

        return AffineTransform.getScaleInstance(scale, scale).createTransformedShape(
                HexDrawUtilities.getHexBorderArea(direction, HexDrawUtilities.CUT_BORDER, 36));
    }

    /**
     * Generates a fill gradient which is rotated and aligned properly for the
     * drawing area for a
     * hex shadow effect in a lower hex.
     */
    private GradientPaint getElevationShadowGP(Coords src, int direction) {
        final Hex srcHex = game.getBoard().getHex(src);
        final Hex destHex = game.getBoard().getHexInDir(src, direction);

        if (destHex == null) {
            return null;
        }

        int ldiff = destHex.getLevel() - srcHex.getLevel();
        // the shadow strength depends on the level difference,
        // but only to a maximum difference of 3 levels
        ldiff = Math.min(ldiff * 5, 15);

        Color c1 = new Color(30, 30, 50, 255); // dark end of shadow
        Color c2 = new Color(50, 50, 70, 0); // light end of shadow

        Point2D p1 = new Point2D.Double(41.5, -25 + ldiff);
        Point2D p2 = new Point2D.Double(41.5, 8.0 + ldiff);

        AffineTransform t = new AffineTransform();
        t.scale(scale, scale);
        t.rotate(Math.toRadians(direction * 60), 41.5, 35.5);
        t.transform(p1, p1);
        t.transform(p2, p2);

        return new GradientPaint(p1, c1, p2, c2);
    }

    /**
     * @return The absolute position of the upper-left hand corner of the hex
     *         graphic
     */
    private Point getHexLocation(int x, int y, boolean ignoreElevation) {
        float elevationAdjust = 0.0f;

        Hex hex = game.getBoard().getHex(x, y);
        if ((hex != null) && useIsometric() && !ignoreElevation) {
            elevationAdjust = hex.getLevel() * HEX_ELEV * scale * -1.0f;
        }
        int ypos = (y * (int) (HEX_H * scale))
                + ((x & 1) == 1 ? (int) ((HEX_H / 2) * scale) : 0);
        return new Point(x * (int) (HEX_WC * scale), ypos + (int) elevationAdjust);
    }

    /**
     * For large tile texture: Returns the absolute position of the upper-left
     * hand corner of the hex graphic When using large tiles multiplying the
     * rounding errors from the (int) cast must be avoided however this cannot
     * be used for small tiles as it will make gaps appear between hexes This
     * will not factor in Isometric as this would be incorrect for large tiles
     */
    static Point getHexLocationLargeTile(int x, int y, float tscale) {
        int ypos = (int) (y * HEX_H * tscale)
                + ((x & 1) == 1 ? (int) ((HEX_H / 2) * tscale) : 0);
        return new Point((int) (x * HEX_WC * tscale), ypos);
    }

    private Point getHexLocationLargeTile(int x, int y) {
        return getHexLocationLargeTile(x, y, scale);
    }

    Point getHexLocation(Coords c) {
        return c == null ? null : getHexLocation(c.getX(), c.getY(), false);
    }

    /**
     * Returns the absolute position of the centre of the hex graphic
     */
    private Point getCentreHexLocation(int x, int y, boolean ignoreElevation) {
        Point p = getHexLocation(x, y, ignoreElevation);
        p.x += ((HEX_W / 2) * scale);
        p.y += ((HEX_H / 2) * scale);
        return p;
    }

    public Point getCentreHexLocation(Coords c) {
        return getCentreHexLocation(c.getX(), c.getY(), false);
    }

    public Point getCentreHexLocation(Coords c, boolean ignoreElevation) {
        return getCentreHexLocation(c.getX(), c.getY(), ignoreElevation);
    }

    public void drawRuler(Coords s, Coords e, Color sc, Color ec) {
        rulerStart = s;
        rulerEnd = e;
        rulerStartColor = sc;
        rulerEndColor = ec;

        boardPanel.repaint();
    }

    public Coords getRulerStart() {
        return rulerStart;
    }

    public Coords getRulerEnd() {
        return rulerEnd;
    }

    @Override
    public Coords getCoordsAt(Point p) {
        // We must account for the board translation to add padding
        p.x -= HEX_W;
        p.y -= HEX_H;

        // base values
        int x = p.x / (int) (HEX_WC * scale);
        int y = p.y / (int) (HEX_H * scale);
        // correction for the displaced odd columns
        if ((float) p.y / (scale * HEX_H) - y < 0.5) {
            y -= x % 2;
        }

        // check the surrounding hexes if they contain p
        // checking at most 3 hexes would be sufficient
        // but which ones? This is failsafer.
        Coords cc = new Coords(x, y);
        if (!HexDrawUtilities.getHexFull(getHexLocation(cc), scale).contains(p)) {
            boolean hasMatch = false;
            for (int dir = 0; dir < 6 && !hasMatch; dir++) {
                Coords cn = cc.translated(dir);
                if (HexDrawUtilities.getHexFull(getHexLocation(cn), scale).contains(p)) {
                    cc = cn;
                    hasMatch = true;
                }
            }
        }

        if (useIsometric()) {
            // When using isometric rendering, a lower hex can obscure the
            // normal hex. Iterate over all hexes from highest to lowest,
            // looking for a hex that contains the selected mouse click point.
            final int minElev = Math.min(0, game.getBoard().getMinElevation());
            final int maxElev = Math.max(0, game.getBoard().getMaxElevation());
            final int delta = (int) Math.ceil(((double) maxElev - minElev) / 3.0f);
            final int minHexSpan = Math.max(y - delta, 0);
            final int maxHexSpan = Math.min(y + delta, game.getBoard().getHeight());
            for (int elev = maxElev; elev >= minElev; elev--) {
                for (int i = minHexSpan; i <= maxHexSpan; i++) {
                    for (int dx = -1; dx < 2; dx++) {
                        Coords c1 = new Coords(x + dx, i);
                        Hex hexAlt = game.getBoard().getHex(c1);
                        if (HexDrawUtilities.getHexFull(getHexLocation(c1), scale).contains(p)
                                && (hexAlt != null) && (hexAlt.getLevel() == elev)) {
                            // Return immediately with highest hex found.
                            return c1;
                        }
                    }
                }
            }
            // nothing found
            return new Coords(-1, -1);
        } else {
            // not Isometric
            return cc;
        }
    }

    @Override
    public void setTooltipProvider(BoardViewTooltipProvider provider) {
        boardViewToolTip = provider;
    }

    public void redrawMovingEntity(Entity entity, Coords position, int facing, int elevation) {
        Integer entityId = entity.getId();
        ArrayList<Integer> spriteKey = getIdAndLoc(entityId, -1);
        EntitySprite sprite = entitySpriteIds.get(spriteKey);
        IsometricSprite isoSprite = isometricSpriteIds.get(spriteKey);
        // We can ignore secondary locations for now, as we don't have moving
        // multi-location entities (will need to change for mobile structures)

        PriorityQueue<EntitySprite> newSprites;
        PriorityQueue<IsometricSprite> isoSprites;
        HashMap<ArrayList<Integer>, EntitySprite> newSpriteIds;
        HashMap<ArrayList<Integer>, IsometricSprite> newIsoSpriteIds;

        // Remove sprite for Entity, so it's not displayed while moving
        if (sprite != null) {
            removeSprite(sprite);
            newSprites = new PriorityQueue<>(entitySprites);
            newSpriteIds = new HashMap<>(entitySpriteIds);

            newSprites.remove(sprite);
            newSpriteIds.remove(spriteKey);

            entitySprites = newSprites;
            entitySpriteIds = newSpriteIds;
        }
        // Remove iso sprite for Entity, so it's not displayed while moving
        if (isoSprite != null) {
            removeSprite(isoSprite);
            isoSprites = new PriorityQueue<>(isometricSprites);
            newIsoSpriteIds = new HashMap<>(isometricSpriteIds);

            isoSprites.remove(isoSprite);
            newIsoSpriteIds.remove(spriteKey);

            isometricSprites = isoSprites;
            isometricSpriteIds = newIsoSpriteIds;
        }

        MovingEntitySprite mSprite = movingEntitySpriteIds.get(entityId);
        ArrayList<MovingEntitySprite> newMovingSprites = new ArrayList<>(movingEntitySprites);
        HashMap<Integer, MovingEntitySprite> newMovingSpriteIds = new HashMap<>(movingEntitySpriteIds);
        // Remove any old movement sprite
        if (mSprite != null) {
            newMovingSprites.remove(mSprite);
        }
        // Create new movement sprite
        if (entity.getPosition() != null) {
            mSprite = new MovingEntitySprite(this, entity, position, facing, elevation);
            newMovingSprites.add(mSprite);
            newMovingSpriteIds.put(entityId, mSprite);
        }

        movingEntitySprites = newMovingSprites;
        movingEntitySpriteIds = newMovingSpriteIds;
    }

    public boolean isMovingUnits() {
        return !movingUnits.isEmpty();
    }

    /**
     * Convenience method for returning a Key value for the entitySpriteIds and
     * isometricSprite
     * maps. The List contains as the first element the Entity ID and as the second
     * element its
     * location ID: either -1 if the Entity has no secondary locations, or the index
     * of its
     * secondary location.
     *
     * @param entityId     The Entity ID
     * @param secondaryLoc the secondary loc index, or -1 for Entities without
     *                     secondary positions
     * @return
     */
    private ArrayList<Integer> getIdAndLoc(Integer entityId, int secondaryLoc) {
        ArrayList<Integer> idLoc = new ArrayList<>(2);
        idLoc.add(entityId);
        idLoc.add(secondaryLoc);
        return idLoc;
    }

    /**
     * Clears the sprite for an entity and prepares it to be re-drawn. Replaces
     * the old sprite with the new! Takes a reference to the Entity object
     * before changes, in case it contained important state information, like
     * DropShips taking off (airborne DropShips lose their secondary hexes). Try
     * to prevent annoying ConcurrentModificationExceptions
     */
    public void redrawEntity(Entity entity) {
        Integer entityId = entity.getId();

        // Remove sprites from backing sprite collections before modifying the
        // entitySprites and isometricSprites. Otherwise orphaned overTerrainSprites
        // or behindTerrainHexSprites can result.
        removeSprites(entitySprites);
        removeSprites(isometricSprites);

        // If the entity we are updating doesn't have a position, ensure we
        // remove all of its old sprites
        if (entity.getPosition() == null) {
            Iterator<EntitySprite> spriteIter;

            // Remove Entity Sprites
            spriteIter = entitySprites.iterator();
            while (spriteIter.hasNext()) {
                EntitySprite sprite = spriteIter.next();
                if (sprite.entity.equals(entity)) {
                    spriteIter.remove();
                }
            }

            // Update ID -> Sprite map
            spriteIter = entitySpriteIds.values().iterator();
            while (spriteIter.hasNext()) {
                EntitySprite sprite = spriteIter.next();
                if (sprite.entity.equals(entity)) {
                    spriteIter.remove();
                }
            }

            Iterator<IsometricSprite> isoSpriteIter;

            // Remove IsometricSprites
            isoSpriteIter = isometricSprites.iterator();
            while (isoSpriteIter.hasNext()) {
                IsometricSprite sprite = isoSpriteIter.next();
                if (sprite.entity.equals(entity)) {
                    isoSpriteIter.remove();
                }
            }

            // Update ID -> Iso Sprite Map
            isoSpriteIter = isometricSpriteIds.values().iterator();
            while (isoSpriteIter.hasNext()) {
                IsometricSprite sprite = isoSpriteIter.next();
                if (sprite.entity.equals(entity)) {
                    isoSpriteIter.remove();
                }
            }
        }

        // Create a copy of the sprite list
        Queue<EntitySprite> newSprites = new PriorityQueue<>(entitySprites);
        HashMap<ArrayList<Integer>, EntitySprite> newSpriteIds = new HashMap<>(entitySpriteIds);
        Queue<IsometricSprite> isoSprites = new PriorityQueue<>(isometricSprites);
        HashMap<ArrayList<Integer>, IsometricSprite> newIsoSpriteIds = new HashMap<>(isometricSpriteIds);

        // Remove the sprites we are going to update
        EntitySprite sprite = entitySpriteIds.get(getIdAndLoc(entityId, -1));
        IsometricSprite isoSprite = isometricSpriteIds.get(getIdAndLoc(entityId, -1));
        if (sprite != null) {
            newSprites.remove(sprite);
        }

        if (isoSprite != null) {
            isoSprites.remove(isoSprite);
        }

        for (int secondaryPos : entity.getSecondaryPositions().keySet()) {
            sprite = entitySpriteIds.get(getIdAndLoc(entityId, secondaryPos));
            if (sprite != null) {
                newSprites.remove(sprite);
            }
            isoSprite = isometricSpriteIds.get(getIdAndLoc(entityId, secondaryPos));
            if (isoSprite != null) {
                isoSprites.remove(isoSprite);
            }
        }

        // Create the new sprites
        Coords position = entity.getPosition();
        boolean canSee = EntityVisibilityUtils.detectedOrHasVisual(localPlayer, game, entity);

        if ((position != null) && canSee) {
            // Add new EntitySprite
            // If no secondary positions, add a sprite for the central position
            if (entity.getSecondaryPositions().isEmpty()) {
                sprite = new EntitySprite(this, entity, -1, radarBlipImage);
                newSprites.add(sprite);
                newSpriteIds.put(getIdAndLoc(entityId, -1), sprite);
            } else { // Add all secondary position sprites, which includes a
                // sprite for the central hex
                for (int secondaryPos : entity.getSecondaryPositions().keySet()) {
                    sprite = new EntitySprite(this, entity, secondaryPos, radarBlipImage);
                    newSprites.add(sprite);
                    newSpriteIds.put(getIdAndLoc(entityId, secondaryPos), sprite);
                }
            }

            // Add new IsometricSprite
            // If no secondary positions, add a sprite for the central position
            if (entity.getSecondaryPositions().isEmpty()) {
                isoSprite = new IsometricSprite(this, entity, -1, radarBlipImage);
                isoSprites.add(isoSprite);
                newIsoSpriteIds.put(getIdAndLoc(entityId, -1), isoSprite);
            } else {
                // Add all secondary position sprites, which includes a
                // sprite for the central hex
                for (int secondaryPos : entity.getSecondaryPositions().keySet()) {
                    isoSprite = new IsometricSprite(this, entity, secondaryPos, radarBlipImage);
                    isoSprites.add(isoSprite);
                    newIsoSpriteIds.put(getIdAndLoc(entityId, secondaryPos), isoSprite);
                }
            }
        }

        // Update Sprite state with new collections
        entitySprites = newSprites;
        entitySpriteIds = newSpriteIds;
        isometricSprites = isoSprites;
        isometricSpriteIds = newIsoSpriteIds;
        addSprites(entitySprites);
        if (drawIsometric) {
            addSprites(isometricSprites);
        }

        // Remove C3 sprites
        for (Iterator<C3Sprite> i = c3Sprites.iterator(); i.hasNext();) {
            final C3Sprite c3sprite = i.next();
            if ((c3sprite.entityId == entity.getId()) || (c3sprite.masterId == entity.getId())) {
                i.remove();
            }
        }

        // Update C3 link, if necessary
        if (entity.hasC3() || entity.hasC3i() || entity.hasActiveNovaCEWS() || entity.hasNavalC3()) {
            addC3Link(entity);
        }

        for (Iterator<VTOLAttackSprite> iter = vtolAttackSprites.iterator(); iter.hasNext();) {
            final VTOLAttackSprite s = iter.next();
            if (s.getEntity().getId() == entity.getId()) {
                iter.remove();
            }
        }

        // Remove Flyover Sprites
        Iterator<FlyOverSprite> flyOverIt = flyOverSprites.iterator();
        while (flyOverIt.hasNext()) {
            final FlyOverSprite flyOverSprite = flyOverIt.next();
            if (flyOverSprite.getEntityId() == entity.getId()) {
                flyOverIt.remove();
            }
        }

        // Add Flyover path, if necessary
        if ((entity.isAirborne() || entity.isMakingVTOLGroundAttack())
                && (entity.getPassedThrough().size() > 1)) {
            addFlyOverPath(entity);
        }

        updateEcmList();
        highlightSelectedEntity(getSelectedEntity());
        scheduleRedraw();
    }

    /**
     * Clears all old entity sprites out of memory and sets up new ones.
     */
    public void redrawAllEntities() {
        int numEntities = game.getNoOfEntities();
        // Prevent IllegalArgumentException
        numEntities = Math.max(1, numEntities);
        Queue<EntitySprite> newSprites = new PriorityQueue<>(numEntities);
        Queue<IsometricSprite> newIsometricSprites = new PriorityQueue<>(numEntities);
        Map<ArrayList<Integer>, EntitySprite> newSpriteIds = new HashMap<>(numEntities);
        Map<ArrayList<Integer>, IsometricSprite> newIsoSpriteIds = new HashMap<>(numEntities);

        ArrayList<WreckSprite> newWrecks = new ArrayList<>();
        ArrayList<IsometricWreckSprite> newIsometricWrecks = new ArrayList<>();

        Board board = game.getBoard();
        Enumeration<Entity> e = game.getWreckedEntities();
        while (e.hasMoreElements()) {
            Entity entity = e.nextElement();
            Coords position = entity.getPosition();
            if (!(entity instanceof Infantry) && (position != null) && board.contains(position)) {
                WreckSprite ws;
                IsometricWreckSprite iws;
                if (entity.getSecondaryPositions().isEmpty()) {
                    ws = new WreckSprite(this, entity, -1);
                    newWrecks.add(ws);
                    iws = new IsometricWreckSprite(this, entity, -1);
                    newIsometricWrecks.add(iws);
                } else {
                    for (int secondaryPos : entity.getSecondaryPositions().keySet()) {
                        ws = new WreckSprite(this, entity, secondaryPos);
                        newWrecks.add(ws);
                        iws = new IsometricWreckSprite(this, entity, secondaryPos);
                        newIsometricWrecks.add(iws);
                    }
                }
            }
        }

        clearC3Networks();
        clearFlyOverPaths();
        for (Entity entity : game.getEntitiesVector()) {
            if (entity.getPosition() == null) {
                continue;
            }
            if ((localPlayer != null)
                    && game.getOptions().booleanOption(OptionsConstants.ADVANCED_DOUBLE_BLIND)
                    && entity.getOwner().isEnemyOf(localPlayer)
                    && !entity.hasSeenEntity(localPlayer)
                    && !entity.hasDetectedEntity(localPlayer)) {
                continue;
            }
            if ((localPlayer != null)
                    && game.getOptions().booleanOption(OptionsConstants.ADVANCED_HIDDEN_UNITS)
                    && entity.getOwner().isEnemyOf(localPlayer)
                    && entity.isHidden()) {
                continue;
            }
            if (entity.getSecondaryPositions().isEmpty()) {
                EntitySprite sprite = new EntitySprite(this, entity, -1, radarBlipImage);
                newSprites.add(sprite);
                newSpriteIds.put(getIdAndLoc(entity.getId(), -1), sprite);
                IsometricSprite isosprite = new IsometricSprite(this, entity, -1, radarBlipImage);
                newIsometricSprites.add(isosprite);
                newIsoSpriteIds.put(getIdAndLoc(entity.getId(), -1), isosprite);
            } else {
                for (int secondaryPos : entity.getSecondaryPositions().keySet()) {
                    EntitySprite sprite = new EntitySprite(this, entity, secondaryPos, radarBlipImage);
                    newSprites.add(sprite);
                    newSpriteIds.put(getIdAndLoc(entity.getId(), secondaryPos), sprite);

                    IsometricSprite isosprite = new IsometricSprite(this, entity, secondaryPos, radarBlipImage);
                    newIsometricSprites.add(isosprite);
                    newIsoSpriteIds.put(getIdAndLoc(entity.getId(), secondaryPos), isosprite);
                }
            }

            if (entity.hasC3() || entity.hasC3i() || entity.hasActiveNovaCEWS() || entity.hasNavalC3()) {
                addC3Link(entity);
            }

            if ((entity.isAirborne() || entity.isMakingVTOLGroundAttack())
                    && (entity.getPassedThrough().size() > 1)) {
                addFlyOverPath(entity);
            }
        }

        removeSprites(entitySprites);
        removeSprites(isometricSprites);

        entitySprites = newSprites;
        entitySpriteIds = newSpriteIds;

        isometricSprites = newIsometricSprites;
        isometricSpriteIds = newIsoSpriteIds;

        addSprites(entitySprites);
        if (drawIsometric) {
            addSprites(isometricSprites);
        }

        wreckSprites = newWrecks;
        isometricWreckSprites = newIsometricWrecks;

        // Update ECM list, to ensure that Sprites are updated with ECM info
        updateEcmList();
        // Re-highlight a selected entity, if present
        highlightSelectedEntity(getSelectedEntity());

        scheduleRedraw();
    }

    /**
     * Moves the cursor to the new position, or hides it, if newPos is null
     */
    private void moveCursor(CursorSprite cursor, Coords newPos) {
        final Rectangle oldBounds = new Rectangle(cursor.getBounds());
        if (newPos != null) {
            cursor.setHexLocation(newPos);
        } else {
            cursor.setOffScreen();
        }
        // repaint affected area
        boardPanel.repaint(oldBounds);
        boardPanel.repaint(cursor.getBounds());
    }

    /**
     * Centers the board on the position of the selected unit, if any. Uses smooth
     * centering if activated
     * in the client settings.
     */
    public void centerOnSelected() {
        centerOn(getSelectedEntity());
    }

    /**
     * Centers the board on the position of the given unit. Uses smooth centering if
     * activated
     * in the client settings. The given entity may be null, in which case nothing
     * happens.
     *
     * @param entity The unit to center on.
     */
    public void centerOn(@Nullable Entity entity) {
        if (entity != null) {
            centerOnHex(entity.getPosition());
        }
    }

    @Override
    public void centerOnHex(@Nullable Coords c) {
        if (c == null) {
            return;
        }

        if (GUIP.getSoftCenter()) {
            // Soft Centering:
            // set the target point
            Point p = getCentreHexLocation(c);
            softCenterTarget.setLocation(
                    (double) p.x / boardSize.getWidth(),
                    (double) p.y / boardSize.getHeight());

            // adjust the target point because the board can't
            // center on points too close to an edge
            double w = scrollpane.getViewport().getWidth();
            double h = scrollpane.getViewport().getHeight();
            double bw = boardSize.getWidth();
            double bh = boardSize.getHeight();

            double minX = (w / 2 - HEX_W) / bw;
            double minY = (h / 2 - HEX_H) / bh;
            double maxX = (bw + HEX_W - w / 2) / bw;
            double maxY = (bh + HEX_H - h / 2) / bh;

            // here the order is important because the top/left
            // edges always stop the board, the bottom/right
            // only when the board is big enough
            softCenterTarget.setLocation(
                    Math.min(softCenterTarget.getX(), maxX),
                    Math.min(softCenterTarget.getY(), maxY));

            softCenterTarget.setLocation(
                    Math.max(softCenterTarget.getX(), minX),
                    Math.max(softCenterTarget.getY(), minY));

            // get the current board center point
            double[] v = getVisibleArea();
            oldCenter.setLocation((v[0] + v[2]) / 2, (v[1] + v[3]) / 2);

            waitTimer = 0;
            isSoftCentering = true;

        } else {
            // no soft centering:
            // center on c directly
            Point p = getCentreHexLocation(c);
            centerOnPointRel(
                    (double) p.x / boardSize.getWidth(),
                    (double) p.y / boardSize.getHeight());
        }
    }

    /**
     * Moves the board one step towards the final
     * position in during soft centering.
     */
    private synchronized void centerOnHexSoftStep(long deltaTime) {
        if (isSoftCentering) {
            // don't move the board if 20ms haven't passed since the last move
            waitTimer += deltaTime;
            if (waitTimer < 20) {
                return;
            }
            waitTimer = 0;

            // move the board by a fraction of the distance to the target
            Point2D newCenter = new Point2D.Double(
                    oldCenter.getX() + (softCenterTarget.getX() - oldCenter.getX()) / SOFT_CENTER_SPEED,
                    oldCenter.getY() + (softCenterTarget.getY() - oldCenter.getY()) / SOFT_CENTER_SPEED);
            centerOnPointRel(newCenter.getX(), newCenter.getY());

            oldCenter = newCenter;

            // stop the motion when close enough to the final position
            if (softCenterTarget.distance(newCenter) < 0.0005) {
                stopSoftCentering();
                pingMinimap();
            }
        }
    }

    public void stopSoftCentering() {
        isSoftCentering = false;
    }

    private void adjustVisiblePosition(@Nullable Coords c, @Nullable Point dispPoint, double ihdx,
            double ihdy) {
        if ((c == null) || (dispPoint == null)) {
            return;
        }

        Point hexPoint = getCentreHexLocation(c);
        // correct for upper left board padding
        hexPoint.translate(HEX_W, HEX_H);
        JScrollBar hscroll = scrollpane.getHorizontalScrollBar();
        hscroll.setValue(hexPoint.x - dispPoint.x + (int) (ihdx * scale * HEX_W));
        JScrollBar vscroll = scrollpane.getVerticalScrollBar();
        vscroll.setValue(hexPoint.y - dispPoint.y + (int) (ihdy * scale * HEX_H));
        pingMinimap();
        boardPanel.repaint();
    }

    /**
     * Centers the board to a point
     *
     * @param xrel the x position relative to board width.
     * @param yrel the y position relative to board height.
     *             Both xrel and yrel should be between 0 and 1.
     *             The method will clip both values to this range.
     */
    public void centerOnPointRel(double xrel, double yrel) {
        // restrict both values to between 0 and 1
        xrel = Math.max(0, xrel);
        xrel = Math.min(1, xrel);
        yrel = Math.max(0, yrel);
        yrel = Math.min(1, yrel);
        Point p = new Point(
                (int) (boardSize.getWidth() * xrel) + HEX_W,
                (int) (boardSize.getHeight() * yrel) + HEX_H);
        JScrollBar vscroll = scrollpane.getVerticalScrollBar();
        vscroll.setValue(p.y - (vscroll.getVisibleAmount() / 2));
        JScrollBar hscroll = scrollpane.getHorizontalScrollBar();
        hscroll.setValue(p.x - (hscroll.getVisibleAmount() / 2));
        boardPanel.repaint();
    }

    /**
     * Returns the currently visible area of the board.
     *
     * @return an array of 4 double values indicating the relative size,
     *         where the first two values indicate the x and y position of the upper
     *         left
     *         corner of the visible area and the second two values the x and y
     *         position of
     *         the lower right corner.
     *         So when the whole board is visible, the values should be 0, 0, 1, 1.
     *         When the lower right corner of the board is visible
     *         and 90% of width and height: 0.1, 0.1, 1, 1
     *         Due to board padding the values can be outside of [0;1]
     */
    public double[] getVisibleArea() {
        double[] values = new double[4];
        double x = scrollpane.getViewport().getViewPosition().getX();
        double y = scrollpane.getViewport().getViewPosition().getY();
        double w = scrollpane.getViewport().getWidth();
        double h = scrollpane.getViewport().getHeight();
        double bw = boardSize.getWidth();
        double bh = boardSize.getHeight();

        values[0] = (x - HEX_W) / bw;
        values[1] = (y - HEX_H) / bh;
        values[2] = (x - HEX_W + w) / bw;
        values[3] = (y - HEX_H + h) / bh;

        return values;
    }

    /**
     * Clears the old movement data and draws the new.
     */
    public void drawMovementData(Entity entity, MovePath md) {
        MoveStep previousStep = null;

        clearMovementData();

        // Nothing to do if we don't have a MovePath
        if (md == null) {
            movementTarget = null;
            return;
        }
        // need to update the movement sprites based on the move path for this
        // entity
        // only way to do this is to clear and refresh (seems wasteful)

        // first get the color for the vector
        Color col = Color.blue;
        if (md.getLastStep() != null) {
            switch (md.getLastStep().getMovementType(true)) {
                case MOVE_RUN:
                case MOVE_VTOL_RUN:
                case MOVE_OVER_THRUST:
                    col = GUIP.getMoveRunColor();
                    break;
                case MOVE_SPRINT:
                case MOVE_VTOL_SPRINT:
                    col = GUIP.getMoveSprintColor();
                    break;
                case MOVE_JUMP:
                    col = GUIP.getMoveJumpColor();
                    break;
                case MOVE_ILLEGAL:
                    col = GUIP.getMoveIllegalColor();
                    break;
                default:
                    col = GUIP.getMoveDefaultColor();
                    break;
            }
            movementTarget = md.getLastStep().getPosition();
        } else {
            movementTarget = null;
        }

        refreshMoveVectors(entity, md, col);

        for (Enumeration<MoveStep> i = md.getSteps(); i.hasMoreElements();) {
            final MoveStep step = i.nextElement();
            if ((null != previousStep)
                    && ((step.getType() == MoveStepType.UP)
                            || (step.getType() == MoveStepType.DOWN)
                            || (step.getType() == MoveStepType.ACC)
                            || (step.getType() == MoveStepType.DEC)
                            || (step.getType() == MoveStepType.ACCN)
                            || (step.getType() == MoveStepType.DECN))) {
                // Mark the previous elevation change sprite hidden
                // so that we can draw a new one in it's place without
                // having overlap.
                pathSprites.get(pathSprites.size() - 1).setHidden(true);
            }

            if (previousStep != null
                    // for advanced movement, we always need to hide prior
                    // because costs will overlap and we only want the current
                    // facing
                    && (game.useVectorMove()
                            // A LAM converting from AirMek to Biped uses two convert steps and we
                            // only want to show the last.
                            || (step.getType() == MoveStepType.CONVERT_MODE
                                    && previousStep.getType() == MoveStepType.CONVERT_MODE)
                            || step.getType() == MoveStepType.BOOTLEGGER)) {
                pathSprites.get(pathSprites.size() - 1).setHidden(true);
            }

            pathSprites.add(new StepSprite(this, step, md.isEndStep(step)));
            previousStep = step;
        }

        displayFlightPathIndicator(md);
        boardPanel.repaint(100);
    }

    /**
     * Add Aerospace ground map flight path indicators on the last step based on how
     * much
     * aerodyne movement is left. This will add sprites along the forward path for
     * the
     * remaining velocity and indicate what point along it's forward path the unit
     * can turn.
     *
     * @param md - Current MovePath that represents the current units movement state
     */
    private void displayFlightPathIndicator(MovePath md) {
        // Don't attempt displaying Flight Path Indicators if using advanced aero
        // movement.
        if (this.game.useVectorMove()) {
            return;
        }

        // Don't calculate any kind of flight path indicators if the move is not legal.
        if (md.getLastStepMovementType() == EntityMovementType.MOVE_ILLEGAL) {
            return;
        }

        // If the unit has remaining aerodyne velocity display the flight path
        // indicators for remaining velocity.
        if ((md.getFinalVelocityLeft() > 0) && !md.nextForwardStepOffBoard()) {
            ArrayList<MoveStep> fpiSteps = new ArrayList<MoveStep>();

            // Cloning the current movement path because we don't want to change it's state.
            MovePath fpiPath = md.clone();

            // While velocity remains, add a forward step to the cloned movement path.
            while (fpiPath.getFinalVelocityLeft() > 0) {
                fpiPath.addStep(MoveStepType.FORWARDS);
                fpiSteps.add(fpiPath.getLastStep());

                // short circuit the flight path indicator if we are off the board.
                if (fpiPath.nextForwardStepOffBoard()) {
                    break;
                }
            }

            // For each hex in the entities forward trajectory, add a flight turn indicator
            // sprite.
            for (MoveStep ms : fpiSteps) {
                // fpiSprites.add(new FlightPathIndicatorSprite(this, ms.getPosition(), ms,
                // fpiPath.isEndStep(ms)));
                fpiSprites.add(
                        new FlightPathIndicatorSprite(this, fpiSteps, fpiSteps.indexOf(ms), fpiPath.isEndStep(ms)));
            }
        }
    }

    /**
     * Clears current movement data from the screen
     */
    public void clearMovementData() {
        pathSprites = new ArrayList<>();
        fpiSprites = new ArrayList<>();
        movementTarget = null;
        checkFoVHexImageCacheClear();
        boardPanel.repaint();
        refreshMoveVectors();
    }

    public void addStrafingCoords(Coords c) {
        strafingCoords.add(c);
    }

    public void clearStrafingCoords() {
        strafingCoords.clear();
    }

    public void setLocalPlayer(Player p) {
        localPlayer = p;
    }

    public Player getLocalPlayer() {
        return localPlayer;
    }

    /**
     * Specifies that this should mark the deployment hexes for a player. If the
     * player is set to null, no hexes will be marked.
     */
    public void markDeploymentHexesFor(Entity ce) {
        en_Deployer = ce;
        boardPanel.repaint(100);
    }

    /**
     * Returns the entity that is currently being deployed
     */
    public Entity getDeployingEntity() {
        return en_Deployer;
    }

    /**
     * add a fly over path to the sprite list
     */
    public void addFlyOverPath(Entity e) {
        if (e.getPosition() == null) {
            return;
        }

        if (e.isMakingVTOLGroundAttack()) {
            vtolAttackSprites.add(new VTOLAttackSprite(this, e));
        }
        flyOverSprites.add(new FlyOverSprite(this, e));
    }

    /**
     * @param c the given coords
     * @return any entities flying over the given coords
     */
    public ArrayList<Entity> getEntitiesFlyingOver(Coords c) {
        ArrayList<Entity> entities = new ArrayList<>();
        for (FlyOverSprite fsprite : flyOverSprites) {
            // Spaceborne units shouldn't count here. They show up incorrectly in the firing
            // display when sensors are in use.
            if (fsprite.getEntity().getPassedThrough().contains(c) && !fsprite.getEntity().isSpaceborne()) {
                entities.add(fsprite.getEntity());
            }
        }
        return entities;
    }

    /**
     * Adds a c3 line to the sprite list.
     */
    public void addC3Link(Entity e) {
        if (e.getPosition() == null) {
            return;
        }

        if (e.hasC3i()) {
            for (Entity fe : game.getEntitiesVector()) {
                if (fe.getPosition() == null) {
                    return;
                }

                if (e.onSameC3NetworkAs(fe) && !fe.equals(e)
                        && !ComputeECM.isAffectedByECM(e, e.getPosition(), fe.getPosition())) {
                    c3Sprites.add(new C3Sprite(this, e, fe));
                }
            }
        } else if (e.hasNavalC3()) {
            for (Entity fe : game.getEntitiesVector()) {
                if (fe.getPosition() == null) {
                    return;
                }

                if (e.onSameC3NetworkAs(fe) && !fe.equals(e)) {
                    c3Sprites.add(new C3Sprite(this, e, fe));
                }
            }
        } else if (e.hasActiveNovaCEWS()) {
            // WOR Nova CEWS
            for (Entity fe : game.getEntitiesVector()) {
                if (fe.getPosition() == null) {
                    return;
                }
                ECMInfo ecmInfo = ComputeECM.getECMEffects(e, e.getPosition(),
                        fe.getPosition(), true, null);
                if (e.onSameC3NetworkAs(fe) && !fe.equals(e) && (ecmInfo != null)
                        && !ecmInfo.isNovaECM()) {
                    c3Sprites.add(new C3Sprite(this, e, fe));
                }
            }
        } else if (e.getC3Master() != null) {
            Entity eMaster = e.getC3Master();
            if (eMaster.getPosition() == null) {
                return;
            }

            // ECM cuts off the network
            boolean blocked = false;
            if (e.hasBoostedC3() && eMaster.hasBoostedC3()) {
                blocked = ComputeECM.isAffectedByAngelECM(e, e.getPosition(), eMaster.getPosition())
                        || ComputeECM.isAffectedByAngelECM(eMaster, eMaster.getPosition(), eMaster.getPosition());
            } else {
                blocked = ComputeECM.isAffectedByECM(e, e.getPosition(), eMaster.getPosition())
                        || ComputeECM.isAffectedByECM(eMaster, eMaster.getPosition(), eMaster.getPosition());
            }

            if (!blocked) {
                c3Sprites.add(new C3Sprite(this, e, e.getC3Master()));
            }
        }
    }

    /**
     * Adds an attack to the sprite list.
     */
    public synchronized void addAttack(AttackAction aa) {
        // Don't make sprites for unknown entities and sensor returns
        Entity ae = game.getEntity(aa.getEntityId());
        Targetable t = game.getTarget(aa.getTargetType(), aa.getTargetId());
        if ((ae == null) || (t == null)
                || (t.getTargetType() == Targetable.TYPE_INARC_POD)
                || (t.getPosition() == null) || (ae.getPosition() == null)) {
            return;
        }
        EntitySprite eSprite = entitySpriteIds.get(getIdAndLoc(ae.getId(),
                (ae.getSecondaryPositions().isEmpty() ? -1 : 0)));
        if (eSprite != null && eSprite.onlyDetectedBySensors()) {
            return;
        }

        boardPanel.repaint(100);
        int attackerId = aa.getEntityId();
        for (AttackSprite sprite : attackSprites) {
            // can we just add this attack to an existing one?
            if ((sprite.getEntityId() == attackerId)
                    && (sprite.getTargetId() == aa.getTargetId())) {
                // use existing attack, but add this weapon
                sprite.addEntityAction(aa);
                rebuildAllSpriteDescriptions(attackerId);
                return;
            }
        }
        // no re-use possible, add a new one
        // don't add a sprite for an artillery attack made by the other player
        if (aa instanceof WeaponAttackAction waa) {
            int ownerId = waa.getEntity(game).getOwner().getId();
            int teamId = waa.getEntity(game).getOwner().getTeam();

            if (aa.getTargetType() != Targetable.TYPE_HEX_ARTILLERY) {
                attackSprites.add(new AttackSprite(this, aa));
            } else if (ownerId == localPlayer.getId() || teamId == localPlayer.getTeam()) {
                attackSprites.add(new AttackSprite(this, aa));
            }
        } else {
            attackSprites.add(new AttackSprite(this, aa));
        }
        rebuildAllSpriteDescriptions(attackerId);
    }

    /**
     * adding a new EntityAction may affect the ToHits of other attacks
     * so rebuild. The underlying data is cached when possible, so the should
     * o the minumum amount of work needed
     */
    void rebuildAllSpriteDescriptions(int attackerId) {
        for (AttackSprite sprite : attackSprites) {
            if (sprite.getEntityId() == attackerId) {
                sprite.rebuildDescriptions();
            }
        }

    }

    /**
     * Removes all attack sprites from a certain entity
     */
    public synchronized void removeAttacksFor(@Nullable Entity e) {
        if (e == null) {
            return;
        }
        int entityId = e.getId();
        for (Iterator<AttackSprite> i = attackSprites.iterator(); i.hasNext();) {
            AttackSprite sprite = i.next();
            if (sprite.getEntityId() == entityId) {
                i.remove();
            }
        }
        boardPanel.repaint(100);
    }

    /**
     * Clears out all attacks and re-adds the ones in the current game.
     */
    public void refreshAttacks() {
        clearAllAttacks();
        for (Enumeration<EntityAction> i = game.getActions(); i.hasMoreElements();) {
            EntityAction ea = i.nextElement();
            if (ea instanceof AttackAction) {
                addAttack((AttackAction) ea);
            }
        }

        for (Enumeration<AttackAction> i = game.getCharges(); i.hasMoreElements();) {
            EntityAction ea = i.nextElement();
            if (ea instanceof PhysicalAttackAction) {
                addAttack((AttackAction) ea);
            }
        }
        boardPanel.repaint(100);
    }

    public void refreshMoveVectors() {
        clearAllMoveVectors();
        for (Entity e : game.getEntitiesVector()) {
            if (e.getPosition() != null) {
                movementSprites.add(new MovementSprite(this, e, e.getVectors(),
                        Color.GRAY, false));
            }
        }
    }

    public void refreshMoveVectors(Entity en, MovePath md, Color col) {
        clearAllMoveVectors();
        // same as normal but when I find the active entity I used the MovePath
        // to get vector
        for (Entity e : game.getEntitiesVector()) {
            if (e.getPosition() != null) {
                if ((en != null) && (e.getId() == en.getId())) {
                    movementSprites.add(new MovementSprite(this, e, md.getFinalVectors(),
                            col, true));
                } else {
                    movementSprites.add(new MovementSprite(this, e, e.getVectors(),
                            col, false));
                }
            }
        }
    }

    public void clearC3Networks() {
        c3Sprites.clear();
    }

    public void clearFlyOverPaths() {
        vtolAttackSprites.clear();
        flyOverSprites.clear();
    }

    /**
     * Clears out all attacks that were being drawn
     */
    public void clearAllAttacks() {
        attackSprites.clear();
    }

    /**
     * Clears out all movement vectors that were being drawn
     */
    public void clearAllMoveVectors() {
        movementSprites.clear();
    }

    private void firstLOSHex(Coords c) {
        if (useLOSTool) {
            moveCursor(secondLOSSprite, null);
            moveCursor(firstLOSSprite, c);
        }
    }

    private void secondLOSHex(Coords c2, Coords c1) {
        if (useLOSTool) {

            Entity ae = chooseEntity(c1);
            Entity te = chooseEntity(c2);

            StringBuffer message = new StringBuffer();
            LosEffects le;
            if ((ae == null) || (te == null)) {
                boolean mekInFirst = GUIP.getMekInFirst();
                boolean mekInSecond = GUIP.getMekInSecond();

                LosEffects.AttackInfo ai = LosEffects.prepLosAttackInfo(
                        game, ae, te, c1, c2, mekInFirst, mekInSecond);

                le = LosEffects.calculateLos(game, ai);
                message.append(Messages.getString("BoardView1.Attacker",
                        mekInFirst ? Messages.getString("BoardView1.Mek")
                                : Messages.getString("BoardView1.NonMek"),
                        c1.getBoardNum()));
                message.append(Messages.getString("BoardView1.Target",
                        mekInSecond ? Messages.getString("BoardView1.Mek")
                                : Messages.getString("BoardView1.NonMek"),
                        c2.getBoardNum()));
            } else {
                le = LosEffects.calculateLOS(game, ae, te);
                message.append(Messages.getString("BoardView1.Attacker", ae.getDisplayName(), c1.getBoardNum()));
                message.append(Messages.getString("BoardView1.Target", te.getDisplayName(), c2.getBoardNum()));
            }
            // Check to see if LoS is blocked
            if (!le.canSee()) {
                message.append(Messages.getString("BoardView1.LOSBlocked", c1.distance(c2)));
                ToHitData thd = le.losModifiers(game);
                message.append("\t" + thd.getDesc() + "\n");
            } else {
                message.append(Messages.getString("BoardView1.LOSNotBlocked", c1.distance(c2)));
                if (le.getHeavyWoods() > 0) {
                    message.append(Messages.getString("BoardView1.HeavyWoods", le.getHeavyWoods()));
                }
                if (le.getLightWoods() > 0) {
                    message.append(Messages.getString("BoardView1.LightWoods", le.getLightWoods()));
                }
                if (le.getLightSmoke() > 0) {
                    message.append(Messages.getString("BoardView1.LightSmoke", le.getLightSmoke()));
                }
                if (le.getHeavySmoke() > 0) {
                    message.append(Messages.getString("BoardView1.HeavySmoke", le.getHeavySmoke()));
                }
                if (le.isTargetCover() && le.canSee()) {
                    message.append(Messages.getString("BoardView1.TargetPartialCover",
                            LosEffects.getCoverName(le.getTargetCover(), true)));
                }
                if (le.isAttackerCover() && le.canSee()) {
                    message.append(Messages.getString("BoardView1.AttackerPartialCover",
                            LosEffects.getCoverName(le.getAttackerCover(), false)));
                }
            }
            JOptionPane.showMessageDialog(boardPanel.getRootPane(), message.toString(),
                    Messages.getString("BoardView1.LOSTitle"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Initializes the various overlay polygons with their vertices.
     */
    public void initPolys() {

        AffineTransform facingRotate = new AffineTransform();

        // facing polygons
        Polygon facingPolyTmp = new Polygon();
        facingPolyTmp.addPoint(41, 3);
        facingPolyTmp.addPoint(35, 9);
        facingPolyTmp.addPoint(41, 7);
        facingPolyTmp.addPoint(42, 7);
        facingPolyTmp.addPoint(48, 9);
        facingPolyTmp.addPoint(42, 3);

        // create the rotated shapes
        facingPolys = new Shape[8];
        for (int dir : allDirections) {
            facingPolys[dir] = facingRotate.createTransformedShape(facingPolyTmp);
            facingRotate.rotate(Math.toRadians(60), HEX_W / 2, HEX_H / 2);
        }

        // final facing polygons
        Polygon finalFacingPolyTmp = new Polygon();
        finalFacingPolyTmp.addPoint(41, 3);
        finalFacingPolyTmp.addPoint(21, 18);
        finalFacingPolyTmp.addPoint(41, 14);
        finalFacingPolyTmp.addPoint(42, 14);
        finalFacingPolyTmp.addPoint(61, 18);
        finalFacingPolyTmp.addPoint(42, 3);

        // create the rotated shapes
        facingRotate.setToIdentity();
        finalFacingPolys = new Shape[8];
        for (int dir : allDirections) {
            finalFacingPolys[dir] = facingRotate.createTransformedShape(finalFacingPolyTmp);
            facingRotate.rotate(Math.toRadians(60), HEX_W / 2, HEX_H / 2);
        }

        // movement polygons
        Polygon movementPolyTmp = new Polygon();
        movementPolyTmp.addPoint(47, 67);
        movementPolyTmp.addPoint(48, 66);
        movementPolyTmp.addPoint(42, 62);
        movementPolyTmp.addPoint(41, 62);
        movementPolyTmp.addPoint(35, 66);
        movementPolyTmp.addPoint(36, 67);

        movementPolyTmp.addPoint(47, 67);
        movementPolyTmp.addPoint(45, 68);
        movementPolyTmp.addPoint(38, 68);
        movementPolyTmp.addPoint(38, 69);
        movementPolyTmp.addPoint(45, 69);
        movementPolyTmp.addPoint(45, 68);

        movementPolyTmp.addPoint(45, 70);
        movementPolyTmp.addPoint(38, 70);
        movementPolyTmp.addPoint(38, 71);
        movementPolyTmp.addPoint(45, 71);
        movementPolyTmp.addPoint(45, 68);

        // create the rotated shapes
        facingRotate.setToIdentity();
        movementPolys = new Shape[8];
        for (int dir : allDirections) {
            movementPolys[dir] = facingRotate.createTransformedShape(movementPolyTmp);
            facingRotate.rotate(Math.toRadians(60), HEX_W / 2, HEX_H / 2);
        }

        // Up and Down Arrows
        facingRotate.setToIdentity();
        facingRotate.translate(0, -31);
        upArrow = facingRotate.createTransformedShape(movementPolyTmp);

        facingRotate.setToIdentity();
        facingRotate.rotate(Math.toRadians(180), HEX_W / 2, HEX_H / 2);
        facingRotate.translate(0, -31);
        downArrow = facingRotate.createTransformedShape(movementPolyTmp);
    }

    synchronized boolean doMoveUnits(long idleTime) {
        boolean movingSomething = false;

        if (!movingUnits.isEmpty()) {
            moveWait += idleTime;

            if (moveWait > GUIP.getMoveStepDelay()) {
                ArrayList<MovingUnit> spent = new ArrayList<>();

                for (MovingUnit move : movingUnits) {
                    movingSomething = true;
                    Entity ge = game.getEntity(move.entity.getId());
                    if (!move.path.isEmpty()) {
                        UnitLocation loc = move.path.get(0);

                        if (ge != null) {
                            redrawMovingEntity(move.entity, loc.getCoords(), loc.getFacing(), loc.getElevation());
                        }
                        move.path.remove(0);
                    } else {
                        if (ge != null) {
                            redrawEntity(ge);
                        }
                        spent.add(move);
                    }

                }

                for (MovingUnit move : spent) {
                    movingUnits.remove(move);
                }
                moveWait = 0;

                if (movingUnits.isEmpty()) {
                    movingEntitySpriteIds.clear();
                    movingEntitySprites.clear();
                    ghostEntitySprites.clear();
                    processBoardViewEvent(new BoardViewEvent(this,
                            BoardViewEvent.FINISHED_MOVING_UNITS));
                }
            }
        }
        return movingSomething;
    }

    //
    // MouseListener
    //
    @Override
    public void mousePressed(MouseEvent me) {
        boardPanel.requestFocusInWindow();
        stopSoftCentering();
        Point point = me.getPoint();

        // Button 4: Hide/Show the minimap and unitDisplay
        if (me.getButton() == 4) {
            if (clientgui != null) {
                clientgui.toggleMMUDDisplays();
            }
        }

        // we clicked the right mouse button,
        // remember the position if we start to scroll
        // if we drag, we should scroll
        if (SwingUtilities.isRightMouseButton(me)) {
            scrollXDifference = me.getX();
            scrollYDifference = me.getY();
            shouldScroll = true;
        }

        if (me.isPopupTrigger() && !dragging) {
            wantsPopup = true;
            return;
        }

        for (IDisplayable disp : overlays) {
            double width = scrollpane.getViewport().getSize().getWidth();
            double height = scrollpane.getViewport().getSize().getHeight();
            Dimension dispDimension = new Dimension();
            dispDimension.setSize(width, height);
            // we need to adjust the point, because it should be against the
            // displayable dimension
            Point dispPoint = new Point();
            dispPoint.setLocation(point.x + boardPanel.getBounds().x, point.y + boardPanel.getBounds().y);
            if (disp.isHit(dispPoint, dispDimension)) {
                return;
            }
        }
        mouseAction(getCoordsAt(point), BOARD_HEX_DRAG, me.getModifiersEx(), me.getButton());
    }

    @Override
    public void mouseReleased(MouseEvent me) {
        // don't show the popup if we are drag-scrolling
        if ((me.isPopupTrigger() || wantsPopup) && !dragging) {
            mouseAction(getCoordsAt(me.getPoint()), BOARD_HEX_POPUP,
                    me.getModifiersEx(), me.getButton());
            // stop scrolling
            shouldScroll = false;
            wantsPopup = false;
            return;
        }

        // if we released the right mouse button, there's no more
        // scrolling
        if (SwingUtilities.isRightMouseButton(me)) {
            scrollXDifference = 0;
            scrollYDifference = 0;
            dragging = false;
            shouldScroll = false;
            wantsPopup = false;
            boardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

        for (IDisplayable disp : overlays) {
            if (disp.isReleased()) {
                return;
            }
        }

        if (me.getClickCount() == 1) {
            mouseAction(getCoordsAt(me.getPoint()), BOARD_HEX_CLICK,
                    me.getModifiersEx(), me.getButton());
        } else {
            mouseAction(getCoordsAt(me.getPoint()), BOARD_HEX_DOUBLECLICK,
                    me.getModifiersEx(), me.getButton());
        }
    }

    @Override
    public void mouseEntered(MouseEvent me) {
    }

    @Override
    public void mouseExited(MouseEvent me) {
        // Reset the tooltip dismissal delay to the preference
        // value so that elements outside the boardview can
        // use tooltips
        if (GUIP.getTooltipDismissDelay() >= 0) {
            ToolTipManager.sharedInstance().setDismissDelay(GUIP.getTooltipDismissDelay());
        } else {
            ToolTipManager.sharedInstance().setDismissDelay(dismissDelay);
        }
    }

    @Override
    public void mouseClicked(MouseEvent me) {

    }

    private static class MovingUnit {
        public Entity entity;

        public ArrayList<UnitLocation> path;

        MovingUnit(Entity entity, Vector<UnitLocation> path) {
            this.entity = entity;
            this.path = new ArrayList<>(path);
        }
    }

    /**
     * Determine if the tile manager's images have been loaded.
     *
     * @return <code>true</code> if all images have been loaded.
     *         <code>false</code> if more need to be loaded.
     */
    public boolean isTileImagesLoaded() {
        return tileManager.isLoaded();
    }

    @Override
    public void setUseLosTool(boolean use) {
        useLOSTool = use;
    }

    public TilesetManager getTilesetManager() {
        return tileManager;
    }

    /**
     * @param lastCursor The lastCursor to set.
     */
    public void setLastCursor(Coords lastCursor) {
        this.lastCursor = lastCursor;
    }

    /**
     * @return Returns the lastCursor.
     */
    public Coords getLastCursor() {
        return lastCursor;
    }

    /**
     * @param highlighted The highlighted to set.
     */
    public void setHighlighted(Coords highlighted) {
        this.highlighted = highlighted;
    }

    /**
     * @param selected The selected to set.
     */
    public void setSelected(Coords selected) {
        if (this.selected != selected) {
            this.selected = selected;
            checkFoVHexImageCacheClear();
        }
    }

    /**
     * @return Returns the selected.
     */
    public Coords getSelected() {
        return selected;
    }

    /**
     * @param firstLOS The firstLOS to set.
     */
    public void setFirstLOS(Coords firstLOS) {
        this.firstLOS = firstLOS;
    }

    /**
     * @return Returns the firstLOS.
     */
    public Coords getFirstLOS() {
        return firstLOS;
    }

    /**
     * Determines if this Board contains the Coords, and if so, "selects" that
     * Coords.
     *
     * @param coords the Coords.
     */
    public void select(Coords coords) {
        if ((coords == null) || game.getBoard().contains(coords)) {
            setSelected(coords);
            moveCursor(selectedSprite, coords);
            moveCursor(firstLOSSprite, null);
            moveCursor(secondLOSSprite, null);
            processBoardViewEvent(new BoardViewEvent(this, coords, null,
                    BoardViewEvent.BOARD_HEX_SELECTED, 0));
        }
    }

    /**
     * "Selects" the specified Coords.
     *
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    public void select(int x, int y) {
        select(new Coords(x, y));
    }

    /**
     * Determines if this Board contains the Coords, and if so, highlights that
     * Coords.
     *
     * @param coords the Coords.
     */
    public void highlight(Coords coords) {
        if ((coords == null) || game.getBoard().contains(coords)) {
            setHighlighted(coords);
            moveCursor(highlightSprite, coords);
            moveCursor(firstLOSSprite, null);
            moveCursor(secondLOSSprite, null);
            processBoardViewEvent(new BoardViewEvent(this, coords, null,
                    BoardViewEvent.BOARD_HEX_HIGHLIGHTED, 0));
        }
    }

    /**
     * @param c The new colour of the highlight cursor.
     */
    public void setHighlightColor(Color c) {
        highlightSprite.setColor(c);
        highlightSprite.prepare();
        boardPanel.repaint();
    }

    /**
     * Highlights the specified Coords.
     *
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    public void highlight(int x, int y) {
        highlight(new Coords(x, y));
    }

    public synchronized void highlightSelectedEntity(Entity e) {
        for (EntitySprite sprite : entitySprites) {
            sprite.setSelected(sprite.entity.equals(e));
        }
    }

    /**
     * Determines if this Board contains the Coords, and if so, "cursors" that
     * Coords.
     *
     * @param coords the Coords.
     */
    public void cursor(Coords coords) {
        if ((coords == null) || game.getBoard().contains(coords)) {
            if ((getLastCursor() == null) || (coords == null) || !coords.equals(getLastCursor())) {
                setLastCursor(coords);
                moveCursor(cursorSprite, coords);
                moveCursor(firstLOSSprite, null);
                moveCursor(secondLOSSprite, null);
                processBoardViewEvent(new BoardViewEvent(this, coords, null,
                        BoardViewEvent.BOARD_HEX_CURSOR, 0));
            } else {
                setLastCursor(coords);
            }
        }
    }

    /**
     * "Cursors" the specified Coords.
     *
     * @param x the x coordinate.
     * @param y the y coordinate.
     */
    public void cursor(int x, int y) {
        cursor(new Coords(x, y));
    }

    public void checkLOS(Coords c) {
        if ((c == null) || game.getBoard().contains(c)) {
            if (getFirstLOS() == null) {
                setFirstLOS(c);
                firstLOSHex(c);
                processBoardViewEvent(new BoardViewEvent(this, c, null,
                        BoardViewEvent.BOARD_FIRST_LOS_HEX, 0));
            } else {
                secondLOSHex(c, getFirstLOS());
                processBoardViewEvent(new BoardViewEvent(this, c, null,
                        BoardViewEvent.BOARD_SECOND_LOS_HEX, 0));
                setFirstLOS(null);
            }
        }
    }

    /**
     * Determines if this Board contains the (x, y) Coords, and if so, notifies
     * listeners about the specified mouse action.
     */
    public void mouseAction(int x, int y, int mtype, int modifiers, int mouseButton) {
        if (game.getBoard().contains(x, y)) {
            Coords c = new Coords(x, y);
            switch (mtype) {
                case BOARD_HEX_CLICK:
                    if ((modifiers & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
                        checkLOS(c);
                    } else {
                        processBoardViewEvent(new BoardViewEvent(this, c, null,
                                BoardViewEvent.BOARD_HEX_CLICKED, modifiers, mouseButton));
                    }
                    break;
                case BOARD_HEX_DOUBLECLICK:
                    processBoardViewEvent(new BoardViewEvent(this, c, null,
                            BoardViewEvent.BOARD_HEX_DOUBLE_CLICKED, modifiers, mouseButton));
                    break;
                case BOARD_HEX_DRAG:
                    processBoardViewEvent(new BoardViewEvent(this, c, null,
                            BoardViewEvent.BOARD_HEX_DRAGGED, modifiers, mouseButton));
                    break;
                case BOARD_HEX_POPUP:
                    processBoardViewEvent(new BoardViewEvent(this, c, null,
                            BoardViewEvent.BOARD_HEX_POPUP, modifiers, mouseButton));
                    break;
            }
        }
    }

    /**
     * Notifies listeners about the specified mouse action.
     *
     * @param coords      - coords the Coords.
     * @param mtype       - Board view event type
     * @param modifiers   - mouse event modifiers mask such as SHIFT_DOWN_MASK etc.
     * @param mouseButton - mouse button associated with this event
     *                    0 = no button
     *                    1 = Button 1
     *                    2 = Button 2
     */
    public void mouseAction(Coords coords, int mtype, int modifiers, int mouseButton) {
        mouseAction(coords.getX(), coords.getY(), mtype, modifiers, mouseButton);
    }

    @Override
    public void boardNewBoard(BoardEvent b) {
        updateBoard();
        game.getBoard().initializeAllAutomaticTerrain();
        clearHexImageCache();
        clearShadowMap();
        boardPanel.repaint();
    }

    @Override
    public void boardChangedHex(BoardEvent b) {
        hexImageCache.remove(b.getCoords());
        // Also repaint the surrounding hexes because of shadows, border etc.
        for (int dir : allDirections) {
            hexImageCache.remove(b.getCoords().translated(dir));
        }
        clearShadowMap();
        boardPanel.repaint();
    }

    @Override
    public synchronized void boardChangedAllHexes(BoardEvent b) {
        clearHexImageCache();
        clearShadowMap();
        boardPanel.repaint();
    }

    private GameListener gameListener = new GameListenerAdapter() {

        @Override
        public void gameEntityNew(GameEntityNewEvent e) {
            updateEcmList();
            redrawAllEntities();
            if (game.getPhase().isMovement()) {
                refreshMoveVectors();
            }
        }

        @Override
        public void gameEntityRemove(GameEntityRemoveEvent e) {
            updateEcmList();
            redrawAllEntities();
            if (game.getPhase().isMovement()) {
                refreshMoveVectors();
            }
        }

        @Override
        public void gameEntityChange(GameEntityChangeEvent e) {
            final Vector<UnitLocation> mp = e.getMovePath();
            final Entity en = e.getEntity();
            final var gopts = game.getOptions();

            updateEcmList();

            // For Entities that have converted to another mode, check for a different
            // sprite
            if (game.getPhase().isMovement() && en.isConvertingNow()) {
                tileManager.reloadImage(en);
            }

            // for units that have been blown up, damaged or ejected, force a reload
            if ((e.getOldEntity() != null) &&
                    ((en.getDamageLevel() != e.getOldEntity().getDamageLevel()) ||
                            (en.isDestroyed() != e.getOldEntity().isDestroyed()) ||
                            (en.getCrew().isEjected() != e.getOldEntity().getCrew().isEjected()))) {
                tileManager.reloadImage(en);
            }

            redrawAllEntities();
            if (game.getPhase().isMovement()) {
                refreshMoveVectors();
            }
            if ((mp != null) && !mp.isEmpty() && GUIP.getShowMoveStep()
                    && !gopts.booleanOption(OptionsConstants.INIT_SIMULTANEOUS_MOVEMENT)) {
                if ((localPlayer == null)
                        || !game.getOptions().booleanOption(OptionsConstants.ADVANCED_DOUBLE_BLIND)
                        || !en.getOwner().isEnemyOf(localPlayer)
                        || en.hasSeenEntity(localPlayer)) {
                    addMovingUnit(en, mp);
                }
            }
        }

        @Override
        public void gameNewAction(GameNewActionEvent e) {
            EntityAction ea = e.getAction();
            if (ea instanceof AttackAction) {
                addAttack((AttackAction) ea);
            }
        }

        @Override
        public void gameBoardNew(GameBoardNewEvent e) {
            Board b = e.getOldBoard();
            if (b != null) {
                b.removeBoardListener(BoardView.this);
            }
            b = e.getNewBoard();
            if (b != null) {
                b.addBoardListener(BoardView.this);
            }
            game.getBoard().initializeAllAutomaticTerrain();
            clearHexImageCache();
            updateBoard();
            clearShadowMap();
        }

        @Override
        public void gameBoardChanged(GameBoardChangeEvent e) {
            clearHexImageCache();
            boardChanged();
        }

        @Override
        public void gamePhaseChange(GamePhaseChangeEvent e) {
            if (GUIP.getGameSummaryBoardView()
                    && (e.getOldPhase().isDeployment() || e.getOldPhase().isMovement()
                            || e.getOldPhase().isTargeting() || e.getOldPhase().isFiring()
                            || e.getOldPhase().isPhysical())) {
                File dir = new File(Configuration.gameSummaryImagesBVDir(), game.getUUIDString());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File imgFile = new File(dir, "round_" + String.format("%03d", game.getRoundCount())
                        + '_' + String.format("%03d", e.getOldPhase().ordinal()) + '_' + e.getOldPhase() + ".png");
                try {
                    ImageIO.write(getEntireBoardImage(false, true), "png", imgFile);
                } catch (Exception ex) {
                    logger.error(ex, "");
                }
            }

            refreshAttacks();

            // Clear some information regardless of what phase it is
            clientgui.clearTemporarySprites();

            switch (e.getNewPhase()) {
                case MOVEMENT:
                    refreshMoveVectors();
                case FIRING:
                    clearAllMoveVectors();
                case PHYSICAL:
                    refreshAttacks();
                    break;
                case INITIATIVE:
                    clearAllAttacks();
                    break;
                case END:
                case VICTORY:
                    clearSprites();
                case LOUNGE:
                    clearHexImageCache();
                    clearAllMoveVectors();
                    clearAllAttacks();
                    clearSprites();
                    select(null);
                    cursor(null);
                    highlight(null);
                default:
            }
            for (Entity en : game.getEntitiesVector()) {
                if ((en.getDamageLevel() != Entity.DMG_NONE) &&
                        ((en.damageThisRound != 0) || (en instanceof GunEmplacement))) {
                    tileManager.reloadImage(en);
                }
            }

        }
    };

    synchronized void boardChanged() {
        redrawAllEntities();
    }

    @Override
    public void clearSprites() {
        pathSprites.clear();
        fpiSprites.clear();
        attackSprites.clear();
        c3Sprites.clear();
        vtolAttackSprites.clear();
        flyOverSprites.clear();
        movementSprites.clear();

        overTerrainSprites.clear();
        behindTerrainHexSprites.clear();

        super.clearSprites();
    }

    public synchronized void updateBoard() {
        updateBoardSize();
        redrawAllEntities();
    }

    /**
     * the old redrawworker converted to a runnable which is called now and then
     * from the event thread
     */
    private class RedrawWorker implements Runnable {

        private long lastTime = System.currentTimeMillis();

        private long currentTime = System.currentTimeMillis();

        @Override
        public void run() {
            currentTime = System.currentTimeMillis();
            if (boardPanel.isShowing()) {
                boolean redraw = false;
                for (IDisplayable disp : overlays) {
                    if (!disp.isSliding()) {
                        disp.setIdleTime(currentTime - lastTime, true);
                    } else {
                        redraw = redraw || disp.slide();
                    }
                }
                redraw = redraw || doMoveUnits(currentTime - lastTime);
                if (redraw) {
                    boardPanel.repaint();
                }
                centerOnHexSoftStep(currentTime - lastTime);
            }
            lastTime = currentTime;
        }
    }

    /**
     * @param e the BoardView's currently selected entity
     */
    public synchronized void selectEntity(Entity e) {
        checkFoVHexImageCacheClear();
        updateEcmList();
        highlightSelectedEntity(e);
    }

    /**
     * Updates maps that determine how to shade hexes affected by E(C)CM. This
     * is expensive, so precalculate only when entity changes occur
     **/
    public void updateEcmList() {
        Map<Coords, Color> newECMHexes = new HashMap<>();
        Map<Coords, Color> newECMCenters = new HashMap<>();
        Map<Coords, Color> newECCMHexes = new HashMap<>();
        Map<Coords, Color> newECCMCenters = new HashMap<>();

        // Compute info about all E(C)CM on the board
        final ArrayList<ECMInfo> allEcmInfo = ComputeECM.computeAllEntitiesECMInfo(game.getEntitiesVector());

        // First, mark the sources of E(C)CM
        // Used for highlighting hexes and tooltips
        for (Entity e : game.getEntitiesVector()) {
            if (e.getPosition() == null) {
                continue;
            }

            boolean entityIsEnemy = e.getOwner().isEnemyOf(localPlayer);

            // If this unit isn't spotted somehow, it's ECM doesn't show up
            if ((localPlayer != null)
                    && game.getOptions().booleanOption(OptionsConstants.ADVANCED_DOUBLE_BLIND)
                    && entityIsEnemy
                    && !e.hasSeenEntity(localPlayer)
                    && !e.hasDetectedEntity(localPlayer)) {
                continue;
            }

            // hidden enemy entities don't show their ECM bubble
            if (entityIsEnemy && e.isHidden()) {
                continue;
            }

            final Color ecmColor = ECMEffects.getECMColor(e.getOwner());
            // Update ECM center information
            if (e.getECMInfo() != null) {
                newECMCenters.put(e.getPosition(), ecmColor);
            }
            // Update ECCM center information
            if (e.getECCMInfo() != null) {
                newECCMCenters.put(e.getPosition(), ecmColor);
            }
            // Update Entity sprite's ECM status
            int secondaryIdx = -1;
            if (!e.getSecondaryPositions().isEmpty()) {
                secondaryIdx = 0;
            }
            EntitySprite eSprite = entitySpriteIds.get(getIdAndLoc(e.getId(), secondaryIdx));
            if (eSprite != null) {
                Coords pos = e.getPosition();
                eSprite.setAffectedByECM(ComputeECM.isAffectedByECM(e, pos, pos, allEcmInfo));
            }
        }

        // Keep track of allied ECM and enemy ECCM
        Map<Coords, ECMEffects> ecmAffectedCoords = new HashMap<>();
        // Keep track of allied ECCM and enemy ECM
        Map<Coords, ECMEffects> eccmAffectedCoords = new HashMap<>();
        for (ECMInfo ecmInfo : allEcmInfo) {
            // Can't see ECM field of unspotted unit
            if ((ecmInfo.getEntity() != null) && (localPlayer != null)
                    && game.getOptions().booleanOption(OptionsConstants.ADVANCED_DOUBLE_BLIND)
                    && ecmInfo.getEntity().getOwner().isEnemyOf(localPlayer)
                    && !ecmInfo.getEntity().hasSeenEntity(localPlayer)
                    && !ecmInfo.getEntity().hasDetectedEntity(localPlayer)) {
                continue;
            }

            // hidden enemy entities don't show their ECM bubble
            if (ecmInfo.getEntity() != null
                    && ecmInfo.getEntity().getOwner().isEnemyOf(localPlayer)
                    && ecmInfo.getEntity().isHidden()) {
                continue;
            }

            final Coords ecmPos = ecmInfo.getPos();
            final int range = ecmInfo.getRange();

            // Add each Coords within range to the list of ECM Coords
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    Coords c = new Coords(x + ecmPos.getX(), y + ecmPos.getY());
                    int dist = ecmPos.distance(c);
                    int dir = ecmInfo.getDirection();
                    // Direction is the facing of the owning Entity
                    boolean inArc = (dir == -1)
                            || Compute.isInArc(ecmPos, dir, c, Compute.ARC_NOSE);
                    if ((dist > range) || !inArc) {
                        continue;
                    }

                    // Check for allied ECCM or enemy ECM
                    if ((!ecmInfo.isOpposed(localPlayer) && ecmInfo.isECCM())
                            || (ecmInfo.isOpposed(localPlayer) && ecmInfo.isECCM())) {
                        ECMEffects ecmEffects = eccmAffectedCoords.get(c);
                        if (ecmEffects == null) {
                            ecmEffects = new ECMEffects();
                            eccmAffectedCoords.put(c, ecmEffects);
                        }
                        ecmEffects.addECM(ecmInfo);
                    } else {
                        ECMEffects ecmEffects = ecmAffectedCoords.get(c);
                        if (ecmEffects == null) {
                            ecmEffects = new ECMEffects();
                            ecmAffectedCoords.put(c, ecmEffects);
                        }
                        ecmEffects.addECM(ecmInfo);
                    }
                }
            }
        }

        // Finally, determine the color for each affected hex
        for (Coords c : ecmAffectedCoords.keySet()) {
            ECMEffects ecm = ecmAffectedCoords.get(c);
            ECMEffects eccm = eccmAffectedCoords.get(c);
            processAffectedCoords(c, ecm, eccm, newECMHexes, newECCMHexes);
        }
        for (Coords c : eccmAffectedCoords.keySet()) {
            ECMEffects ecm = ecmAffectedCoords.get(c);
            ECMEffects eccm = eccmAffectedCoords.get(c);
            // Already processed all ECM affected coords
            if (ecm != null) {
                continue;
            }
            processAffectedCoords(c, ecm, eccm, newECMHexes, newECCMHexes);
        }

        Set<Coords> updatedHexes = new HashSet<>();
        if (ecmHexes != null) {
            updatedHexes.addAll(ecmHexes.keySet());
        }
        if (eccmHexes != null) {
            updatedHexes.addAll(eccmHexes.keySet());
        }
        updatedHexes.addAll(newECMHexes.keySet());
        updatedHexes.addAll(newECCMHexes.keySet());
        clearHexImageCache(updatedHexes);

        synchronized (this) {
            ecmHexes = newECMHexes;
            ecmCenters = newECMCenters;
            eccmHexes = newECCMHexes;
            eccmCenters = newECCMCenters;
        }

        boardPanel.repaint();
    }

    private void processAffectedCoords(Coords c, ECMEffects ecm,
            ECMEffects eccm, Map<Coords, Color> newECMHexes,
            Map<Coords, Color> newECCMHexes) {
        Color hexColorECM = null;
        if (ecm != null) {
            hexColorECM = ecm.getHexColor();
        }
        Color hexColorECCM = null;
        if (eccm != null) {
            hexColorECCM = eccm.getHexColor();
        }
        // Hex color is null if all effects cancel out
        if ((hexColorECM == null) && (hexColorECCM == null)) {
            return;
        } else if ((hexColorECM != null) && (hexColorECCM == null)) {
            if (ecm.isECCM()) {
                newECCMHexes.put(c, hexColorECM);
            } else {
                newECMHexes.put(c, hexColorECM);
            }
        } else if ((hexColorECM == null) && (hexColorECCM != null)) {
            if (eccm.isECCM()) {
                newECCMHexes.put(c, hexColorECCM);
            } else {
                newECMHexes.put(c, hexColorECCM);
            }
        } else { // Both are non-null
            newECMHexes.put(c, hexColorECM);
            newECCMHexes.put(c, hexColorECCM);
        }
    }

    /**
     * Have the player select an Entity from the entities at the given coords.
     *
     * @param pos - the <code>Coords</code> containing targets.
     */
    private Entity chooseEntity(Coords pos) {
        // Assume that we have *no* choice.
        Entity choice = null;

        // Get the available choices.
        java.util.List<Entity> entities = game.getEntitiesVector(pos);

        // Do we have a single choice?
        if (entities.size() == 1) {
            // Return that choice.
            choice = entities.get(0);
        } else if (entities.size() > 1) {
            // If we have multiple choices, display a selection dialog.
            choice = EntityChoiceDialog.showSingleChoiceDialog(clientgui.getFrame(),
                    "BoardView1.ChooseEntityDialog.title",
                    Messages.getString("BoardView1.ChooseEntityDialog.message", pos.getBoardNum()),
                    entities);
        }

        // Return the chosen unit.
        return choice;
    }

    @Override
    public Component getComponent() {
        return getComponent(false);
    }

    @Override
    public void setDisplayInvalidFields(boolean displayInvalidFields) {
        displayInvalidHexInfo = displayInvalidFields;
    }

    @Override
    public void setLocalPlayer(int playerId) {
        setLocalPlayer(game.getPlayer(playerId));
    }

    public Component getComponent(boolean scrollBars) {
        // If we're already configured, return the scrollpane
        if (scrollpane != null) {
            return scrollpane;
        }

        SkinSpecification bvSkinSpec = SkinXMLHandler.getSkin(UIComponents.BoardView.getComp());

        // Setup background icons
        try {
            File file;
            if (!bvSkinSpec.backgrounds.isEmpty()) {
                file = new MegaMekFile(Configuration.widgetsDir(), bvSkinSpec.backgrounds.get(0)).getFile();
                if (!file.exists()) {
                    logger.error("BoardView1 Error: icon doesn't exist: " + file.getAbsolutePath());
                } else {
                    bvBgImage = (BufferedImage) ImageUtil.loadImageFromFile(file.getAbsolutePath());
                    bvBgShouldTile = bvSkinSpec.tileBackground;
                }
            }
            if (bvSkinSpec.backgrounds.size() > 1) {
                file = new MegaMekFile(Configuration.widgetsDir(), bvSkinSpec.backgrounds.get(1)).getFile();
                if (!file.exists()) {
                    logger.error("BoardView1 Error: icon doesn't exist: " + file.getAbsolutePath());
                } else {
                    scrollPaneBgImg = ImageUtil.loadImageFromFile(file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            logger.error(ex, "Error loading BoardView background images!");
        }

        // Place the board viewer in a set of scrollbars.
        scrollpane = new JScrollPane(boardPanel) {
            @Override
            protected void paintComponent(Graphics g) {
                if (scrollPaneBgImg == null) {
                    super.paintComponent(g);
                    return;
                }
                int w = getWidth();
                int h = getHeight();
                int iW = scrollPaneBgImg.getWidth(null);
                int iH = scrollPaneBgImg.getHeight(null);
                if ((scrollPaneBgBuffer == null)
                        || (scrollPaneBgBuffer.getWidth() != w)
                        || (scrollPaneBgBuffer.getHeight() != h)) {
                    scrollPaneBgBuffer = new BufferedImage(w, h,
                            BufferedImage.TYPE_INT_RGB);
                    Graphics bgGraph = scrollPaneBgBuffer.getGraphics();
                    // If the unit icon not loaded, prevent infinite loop
                    if ((iW < 1) || (iH < 1)) {
                        return;
                    }
                    for (int x = 0; x < w; x += iW) {
                        for (int y = 0; y < h; y += iH) {
                            bgGraph.drawImage(scrollPaneBgImg, x, y, null);
                        }
                    }
                    bgGraph.dispose();
                }
                g.drawImage(scrollPaneBgBuffer, 0, 0, null);
            }
        };
        scrollpane.setBorder(new MegaMekBorder(bvSkinSpec));
        scrollpane.setLayout(new ScrollPaneLayout());
        // we need to use the simple scroll mode because otherwise the
        // IDisplayables that are drawn in fixed positions in the viewport
        // leave artifacts when scrolling
        scrollpane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        // Prevent the default arrow key scrolling
        scrollpane.getActionMap().put("unitScrollRight", DoNothing);
        scrollpane.getActionMap().put("unitScrollDown", DoNothing);
        scrollpane.getActionMap().put("unitScrollLeft", DoNothing);
        scrollpane.getActionMap().put("unitScrollUp", DoNothing);

        vbar = scrollpane.getVerticalScrollBar();
        hbar = scrollpane.getHorizontalScrollBar();

        if (!scrollBars && !bvSkinSpec.showScrollBars) {
            vbar.setPreferredSize(new Dimension(0, vbar.getHeight()));
            hbar.setPreferredSize(new Dimension(hbar.getWidth(), 0));
        }

        return scrollpane;
    }

    AbstractAction DoNothing = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent evt) {

        }
    };

    public void refreshDisplayables() {
        boardPanel.repaint();
    }

    private void pingMinimap() {
        // send the minimap a hex moused event to make it
        // update the visible area rectangle
        BoardViewEvent bve = new BoardViewEvent(this, BoardViewEvent.BOARD_HEX_DRAGGED);
        for (BoardViewListener l : boardViewListeners) {
            l.hexMoused(bve);
        }
    }

    public void showPopup(Object popup, Coords c) {
        Point p = getHexLocation(c);
        p.x += ((int) (HEX_WC * scale) - scrollpane.getX()) + HEX_W;
        p.y += ((int) ((HEX_H * scale) / 2) - scrollpane.getY()) + HEX_H;
        if (((JPopupMenu) popup).getParent() == null) {
            boardPanel.add((JPopupMenu) popup);
        }
        ((JPopupMenu) popup).show(boardPanel, p.x, p.y);
    }

    @Override
    public void zoomIn() {
        if (zoomIndex == (ZOOM_FACTORS.length - 1)) {
            return;
        }
        zoomIndex++;
        zoom();
    }

    @Override
    public void zoomOut() {
        if (zoomIndex == 0) {
            return;
        }
        zoomIndex--;
        zoom();
    }

    private void checkZoomIndex() {
        if (zoomIndex > (ZOOM_FACTORS.length - 1)) {
            zoomIndex = ZOOM_FACTORS.length - 1;
        }
        if (zoomIndex < 0) {
            zoomIndex = 0;
        }
    }

    /**
     * Changes hex dimensions and refreshes the map with the new scale
     */
    private void zoom() {
        checkZoomIndex();
        stopSoftCentering();
        scale = ZOOM_FACTORS[zoomIndex];
        GUIP.setMapZoomIndex(zoomIndex);

        hex_size = new Dimension((int) (HEX_W * scale), (int) (HEX_H * scale));

        scaledImageCache = new ImageCache<>();

        cursorSprite.prepare();
        highlightSprite.prepare();
        selectedSprite.prepare();
        firstLOSSprite.prepare();
        secondLOSSprite.prepare();

        allSprites.forEach(Sprite::prepare);

        updateFontSizes();
        updateBoard();

        for (StepSprite sprite : pathSprites) {
            sprite.refreshZoomLevel();
        }

        for (FlightPathIndicatorSprite sprite : fpiSprites) {
            sprite.prepare();
        }

        boardPanel.setSize(boardSize);

        clearHexImageCache();
        boardPanel.repaint();
    }

    private void updateFontSizes() {
        if (zoomIndex < 7) {
            font_elev = FONT_7;
            font_hexnum = FONT_7;
            font_minefield = FONT_7;
        } else if ((zoomIndex < 8)) {
            font_elev = FONT_10;
            font_hexnum = FONT_10;
            font_minefield = FONT_10;
        } else if ((zoomIndex < 10)) {
            font_elev = FONT_12;
            font_hexnum = FONT_12;
            font_minefield = FONT_12;
        } else if ((zoomIndex < 11)) {
            font_elev = FONT_14;
            font_hexnum = FONT_14;
            font_minefield = FONT_14;
        } else if (zoomIndex < 12) {
            font_elev = FONT_16;
            font_hexnum = FONT_16;
            font_minefield = FONT_16;
        } else if (zoomIndex < 13) {
            font_elev = FONT_18;
            font_hexnum = FONT_18;
            font_minefield = FONT_18;
        } else {
            font_elev = FONT_24;
            font_hexnum = FONT_24;
            font_minefield = FONT_24;
        }
    }

    /**
     * Return a scaled version of the input. If the useCache flag is set, the
     * scaled image will be stored in an image cache for later retrieval.
     *
     * @param base     The image to get a scaled copy of. The current zoom level
     *                 is used to determine the scale.
     * @param useCache This flag determines whether the scaled image should
     *                 be stored in a cache for later retrieval.
     */
    @Nullable
    Image getScaledImage(Image base, boolean useCache) {
        if (base == null) {
            return null;
        }

        if (zoomIndex == BASE_ZOOM_INDEX) {
            return base;
        }

        Image scaled;
        if (useCache) {
            // Check the cache
            scaled = scaledImageCache.get(base.hashCode());
        } else {
            scaled = null;
        }
        // Compute the scaled image
        if (scaled == null) {
            MediaTracker tracker = new MediaTracker(boardPanel);
            if ((base.getWidth(null) == -1) || (base.getHeight(null) == -1)) {
                tracker.addImage(base, 0);
                try {
                    tracker.waitForID(0);
                } catch (InterruptedException e) {
                    logger.error(e, "");
                }
                if (tracker.isErrorAny()) {
                    return null;
                }
                tracker.removeImage(base);
            }
            int width = (int) (base.getWidth(null) * scale);
            int height = (int) (base.getHeight(null) * scale);

            if ((width < 1) || (height < 1)) {
                return null;
            }

            scaled = scale(base, width, height);
            tracker.addImage(scaled, 1);
            // Wait for image to load
            try {
                tracker.waitForID(1);
            } catch (InterruptedException e) {
                logger.error(e, "");
            }
            tracker.removeImage(scaled);
            // Cache the image if the flag is set
            if (useCache) {
                scaledImageCache.put(base.hashCode(), scaled);
            }
        }
        return scaled;
    }

    /**
     * The actual scaling code.
     */
    private Image scale(Image img, int width, int height) {
        return ImageUtil.getScaledImage(img, width, height,
                ZOOM_SCALE_TYPES[zoomIndex]);
    }

    public boolean toggleIsometric() {
        drawIsometric = !drawIsometric;
        allSprites.forEach(Sprite::prepare);

        clearHexImageCache();
        updateBoard();
        boardPanel.repaint();
        return drawIsometric;
    }

    public void updateEntityLabels() {
        for (Entity e : game.getEntitiesVector()) {
            e.generateShortName();
        }

        for (EntitySprite eS : entitySprites) {
            eS.prepare();
        }

        for (IsometricSprite eS : isometricSprites) {
            eS.prepare();
        }
        boardPanel.repaint();
    }

    BufferedImage createShadowMask(Image image) {
        int hashCode = image.hashCode();
        BufferedImage mask = shadowImageCache.get(hashCode);
        if (mask != null) {
            return mask;
        }
        mask = new BufferedImage(image.getWidth(null),
                image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        float opacity = 0.4f;
        Graphics2D g2d = mask.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN,
                opacity));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, image.getWidth(null), image.getHeight(null));
        g2d.dispose();
        shadowImageCache.put(hashCode, mask);
        return mask;
    }

    /**
     * Returns true if the BoardView has an active chatter box else false.
     *
     * @return
     */
    public boolean getChatterBoxActive() {
        return chatterBoxActive;
    }

    /**
     * Sets whether the BoardView has an active chatter box or not.
     *
     * @param cba
     */
    public void setChatterBoxActive(boolean cba) {
        chatterBoxActive = cba;
    }

    public void setShouldIgnoreKeys(boolean shouldIgnoreKeys) {
        this.shouldIgnoreKeys = shouldIgnoreKeys;
    }

    public void clearHexImageCache() {
        hexImageCache.clear();
    }

    /**
     * Clear a specific list of Coords from the hex image cache.
     *
     * @param coords
     */
    public void clearHexImageCache(Set<Coords> coords) {
        for (Coords c : coords) {
            hexImageCache.remove(c);
        }
    }

    /**
     * Check to see if the HexImageCache should be cleared because of field-of-view
     * changes.
     */
    public void checkFoVHexImageCacheClear() {
        boolean darken = shouldFovDarken();
        boolean highlight = shouldFovHighlight();
        if (darken || highlight) {
            clearHexImageCache();
        }
    }

    public static Polygon getHexPoly() {
        return HEX_POLY;
    }

    /**
     * Displays a dialog and changes the theme of all
     * board hexes to the user-chosen theme.
     */
    public @Nullable String changeTheme() {
        if (game == null) {
            return null;
        }
        Board board = game.getBoard();
        if (board.inSpace()) {
            return null;
        }

        Set<String> themes = tileManager.getThemes();
        if (themes.remove("")) {
            themes.add("(No Theme)");
        }
        themes.add("(Original Theme)");

        setShouldIgnoreKeys(true);
        selectedTheme = (String) JOptionPane.showInputDialog(
                null,
                "Choose the desired theme:",
                "Theme Selection",
                JOptionPane.PLAIN_MESSAGE,
                null,
                themes.toArray(),
                selectedTheme);
        setShouldIgnoreKeys(false);

        if (selectedTheme == null) {
            return null;
        } else if (selectedTheme.equals("(Original Theme)")) {
            selectedTheme = null;
        } else if (selectedTheme.equals("(No Theme)")) {
            selectedTheme = "";
        }

        board.setTheme(selectedTheme);
        return selectedTheme;
    }

    public Rectangle getDisplayablesRect() {
        return displayablesRect;
    }

    public boolean shouldFovHighlight() {
        return GUIP.getFovHighlight() && !(game.getPhase().isReport());
    }

    public boolean shouldFovDarken() {
        return GUIP.getFovDarken() && !(game.getPhase().isReport());
    }

    public void setShowLobbyPlayerDeployment(boolean b) {
        showLobbyPlayerDeployment = b;
    }

    @Override
    public JPanel getPanel() {
        return boardPanel;
    }

    @Override
    public Dimension getBoardSize() {
        return boardSize;
    }

    @Override
    public Set<Integer> getAnimatedImages() {
        return animatedImages;
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle arg0, int arg1, int arg2) {
        return (int) (scale / 2.0) * ((arg1 == SwingConstants.VERTICAL) ? HEX_H : HEX_W);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle arg0, int arg1, int arg2) {
        Dimension size = scrollpane.getViewport().getSize();
        return (arg1 == SwingConstants.VERTICAL) ? size.height : size.width;
    }

    @Override
    public void dispose() {
        super.dispose();
        redrawTimerTask.cancel();
        fovHighlightingAndDarkening.die();
        KeyBindParser.removePreferenceChangeListener(this);
        GUIP.removePreferenceChangeListener(this);
        PreferenceManager.getClientPreferences().removePreferenceChangeListener(this);
    }

    /** @return The TurnDetailsOverlay if this boardview has one. */
    @Nullable
    public TurnDetailsOverlay getTurnDetailsOverlay() {
        return (TurnDetailsOverlay) overlays.stream()
                .filter(o -> o instanceof TurnDetailsOverlay)
                .findFirst().orElse(null);
    }

    @Nullable
    Entity getSelectedEntity() {
        return clientgui != null ? clientgui.getDisplayedUnit() : null;
    }

    public FovHighlightingAndDarkening getFovHighlighting() {
        return fovHighlightingAndDarkening;
    }

    public ArrayList<WreckSprite> getWreckSprites() {
        return wreckSprites;
    }

    public ArrayList<IsometricWreckSprite> getIsoWreckSprites() {
        return isometricWreckSprites;
    }

    ArrayList<AttackSprite> getAttackSprites() {
        return attackSprites;
    }

    @Override
    public void repaint() {
        boardPanel.repaint();
    }

    @Override
    public void addSprites(Collection<? extends Sprite> sprites) {
        super.addSprites(sprites);
        sprites.stream()
                .filter(s -> !(s instanceof HexSprite) || !((HexSprite) s).isBehindTerrain())
                .forEach(overTerrainSprites::add);
        sprites.stream()
                .filter(s -> s instanceof HexSprite)
                .map(s -> (HexSprite) s)
                .filter(HexSprite::isBehindTerrain)
                .forEach(behindTerrainHexSprites::add);
    }

    @Override
    public void removeSprites(Collection<? extends Sprite> sprites) {
        super.removeSprites(sprites);
        overTerrainSprites.removeAll(sprites);
        behindTerrainHexSprites.removeAll(sprites);
    }
}
