package com.k2040.escaagnellis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.BidiFormatter;
import android.text.InputType;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.net.Uri;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_JSON = 1401;
    private static final int REQUEST_IMPORT_JSON = 1402;
    private static final int REQUEST_EXPORT_PDF = 1403;
    private static final int REQUEST_EXPORT_COMPANION_BACKUP = 1404;
    private static final int REQUEST_IMPORT_COMPANION_BACKUP = 1405;
    private static final String STATE_PDF_PREFIX = "pending_pdf_";

    private EscaView escaView;
    private PyramidPdfExportDialog.Config pendingPdfExportConfig;

    @Override
    @SuppressLint("AppBundleLocaleChanges")
    protected void attachBaseContext(Context newBase) {
        SharedPreferences uiPreferences = newBase.getSharedPreferences(
                AppLanguage.PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        Configuration currentConfiguration = newBase.getResources().getConfiguration();
        Locale[] systemLocales = new Locale[currentConfiguration.getLocales().size()];
        for (int i = 0; i < systemLocales.length; i++) {
            systemLocales[i] = currentConfiguration.getLocales().get(i);
        }
        Locale locale = AppLanguage.resolveLocale(
                uiPreferences.getString(AppLanguage.PREFERENCE_KEY, AppLanguage.SYSTEM),
                systemLocales);
        Configuration configuration = new Configuration(currentConfiguration);
        AppLanguage.applyTo(configuration, locale);
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pendingPdfExportConfig =
                PyramidPdfExportDialog.Config.readFromBundle(savedInstanceState, STATE_PDF_PREFIX);
        escaView = new EscaView(this);
        setContentView(escaView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (escaView != null) escaView.onHostResumed();
    }

    @Override
    protected void onPause() {
        if (escaView != null) escaView.onHostPaused();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (escaView != null && escaView.handleBackPressed()) return;
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingPdfExportConfig != null) {
            pendingPdfExportConfig.writeToBundle(outState, STATE_PDF_PREFIX);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EXPORT_JSON) {
            if (escaView != null) {
                Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
                escaView.handleExportDocumentResult(uri);
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_JSON) {
            if (escaView != null) {
                Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
                escaView.handleImportDocumentResult(uri);
            }
            return;
        }
        if (requestCode == REQUEST_EXPORT_PDF) {
            PyramidPdfExportDialog.Config config = pendingPdfExportConfig;
            pendingPdfExportConfig = null;
            if (escaView != null) {
                Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
                escaView.handlePdfDocumentResult(uri, config);
            }
            return;
        }
        if (requestCode == REQUEST_EXPORT_COMPANION_BACKUP) {
            if (escaView != null) {
                Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
                escaView.handleExportCompanionBackupResult(uri);
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_COMPANION_BACKUP) {
            if (escaView != null) {
                Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
                escaView.handleImportCompanionBackupResult(uri);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static final class EscaView extends View {
        private static final int TAB_OVERVIEW = 0;
        private static final int TAB_TODAY = 1;
        private static final int TAB_STATS = 2;
        private static final int TAB_PORTIONS = 3;

        private static final int CAT_RED = 0;
        private static final int CAT_YELLOW = 1;
        private static final int CAT_GREEN = 2;
        private static final int CAT_WATER = 3;

        private static final int TYPE_DESSERT = 0;
        private static final int TYPE_OIL = 1;
        private static final int TYPE_BUTTER = 2;
        private static final int TYPE_MILK_CHEESE = 3;
        private static final int TYPE_PROTEIN = 4;
        private static final int TYPE_WHEAT = 5;
        private static final int TYPE_WHEAT_POTATO = 6;
        private static final int TYPE_PRODUCE = 7;
        private static final int TYPE_WATER = 9;
        private static final int TYPE_NUTS = 10;
        private static final int TYPE_EXTRA = 11;

        private static final int GROUP_EXTRAS = 0;
        private static final int GROUP_OILS_FATS = 1;
        private static final int GROUP_NUTS_SEEDS = 2;
        private static final int GROUP_MILK_DAIRY = 3;
        private static final int GROUP_PROTEIN = 4;
        private static final int GROUP_GRAINS_SIDES = 5;
        private static final int GROUP_PRODUCE = 6;
        private static final int GROUP_DRINKS = 7;
        private static final int GROUP_COUNT = 8;

        private static final int MODE_SYSTEM = 0;
        private static final int MODE_LIGHT = 1;
        private static final int MODE_DARK = 2;

        private static final int SKIN_STANDARD = 0;
        private static final int SKIN_PASTEL_COZY = 1;
        private static final int MAX_VISIBLE_EXTRAS = 3;

        private static final int BASE_TILE_COUNT = PyramidScheme.TILE_COUNT;
        private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final int OVERLAY_SCROLL_NONE = 0;
        private static final int OVERLAY_SCROLL_LICENSE = 1;
        private static final int OVERLAY_SCROLL_PORTION_INFO = 2;
        private static final int OVERLAY_SCROLL_FIRST_LAUNCH = 3;
        private static final int OVERLAY_SCROLL_SETTINGS = 4;
        private static final int SETTINGS_MENU_NONE = 0;
        private static final int SETTINGS_MENU_LANGUAGE = 1;
        private static final int SETTINGS_MENU_THEME = 2;
        private static final String FIRST_LAUNCH_ACK_VERSION_KEY = "first_launch_notice_ack_version";
        private static final int FIRST_LAUNCH_NOTICE_VERSION = 1;
        private static final float SETTINGS_ROW_HEIGHT_DP = 46f;
        private static final float SETTINGS_SECTION_TOP_GAP_DP = 12f;
        private static final float SETTINGS_SECTION_TO_CARD_GAP_DP = 18f;
        private static final float SETTINGS_CARD_GAP_DP = 14f;
        private static final float SETTINGS_SECTION_GAP_DP = 24f;
        private static final float SETTINGS_MENU_TOP_GAP_DP = 8f;
        private static final float SETTINGS_MENU_ROW_HEIGHT_DP = 42f;
        private static final float SETTINGS_MENU_ROW_GAP_DP = 6f;
        private static final float SETTINGS_MENU_BOTTOM_GAP_DP = 16f;
        private static final float SETTINGS_BOTTOM_GAP_DP = 12f;
        private static final float COMPANION_POSE_CANVAS_PX = 512f;
        // Every full-page pose uses one 84dp scale for its original 512px canvas.
        private static final float COMPANION_POSE_CANVAS_DP = 84f;
        // Standing poses share one floor location; offsets below remain deliberately small.
        private static final float COMPANION_STANDING_ANCHOR_X_FRACTION = .47f;
        private static final float COMPANION_STANDING_FLOOR_Y_FRACTION = .67f;
        private static final float COMPANION_EATING_OFFSET_X_FRACTION = -.005f;
        private static final float COMPANION_CUDDLE_OFFSET_Y_FRACTION = .025f;
        // Sleeping centers its cleaned visible bounds over the hay bed.
        private static final float COMPANION_SLEEPING_ANCHOR_X_FRACTION = .50f;
        private static final float COMPANION_SLEEPING_CENTER_Y_FRACTION = .415f;
        private static final int COMPANION_BOUNDS_ALPHA_THRESHOLD = 8;
        private static final int COMPANION_BOUNDS_PADDING_PX = 2;
        private static final float COMPANION_PROP_GAP_FRACTION = .035f;
        private static final float COMPANION_BOWL_MAX_WIDTH_DP = 32f;
        private static final float COMPANION_BOWL_WIDTH_FRACTION = .40f;
        private static final float COMPANION_BALL_MAX_WIDTH_DP = 24f;
        private static final float COMPANION_BALL_WIDTH_FRACTION = .27f;
        private static final float COMPANION_BALL_TRAVEL_FRACTION = .12f;
        private static final float COMPANION_BALL_BOUNCE_HEIGHT_FRACTION = .35f;
        private static final float[] COMPANION_HEART_SIZE_FRACTIONS = {.14f, .20f, .12f, .17f};
        private static final float[] COMPANION_HEART_HORIZONTAL_OFFSETS = {-.30f, -.08f, .18f, .36f};
        private static final float[] COMPANION_HEART_VERTICAL_OFFSETS = {.02f, -.20f, -.08f, -.34f};
        private static final int[] COMPANION_HEART_ALPHAS = {225, 255, 210, 235};

        private static final RowDef[] ROWS = new RowDef[] {
                new RowDef(CAT_RED, new int[] { PyramidScheme.SUBTYPE_EXTRAS }),
                new RowDef(CAT_YELLOW, new int[] {
                        PyramidScheme.SUBTYPE_OILS_FATS,
                        PyramidScheme.SUBTYPE_NUTS_SEEDS
                }),
                new RowDef(CAT_YELLOW, new int[] {
                        PyramidScheme.SUBTYPE_MILK_DAIRY,
                        PyramidScheme.SUBTYPE_PROTEIN
                }),
                new RowDef(CAT_GREEN, new int[] {
                        PyramidScheme.SUBTYPE_GRAINS,
                        PyramidScheme.SUBTYPE_SIDES
                }),
                new RowDef(CAT_GREEN, new int[] { PyramidScheme.SUBTYPE_PRODUCE }),
                new RowDef(CAT_GREEN, new int[] { PyramidScheme.SUBTYPE_DRINKS })
        };

        private static final String[] GROUP_IDS = PyramidScheme.GROUP_IDS;

        private static final int[] GROUP_BY_SUBTYPE = new int[] {
                GROUP_EXTRAS,
                GROUP_OILS_FATS,
                GROUP_NUTS_SEEDS,
                GROUP_MILK_DAIRY,
                GROUP_PROTEIN,
                GROUP_GRAINS_SIDES,
                GROUP_GRAINS_SIDES,
                GROUP_PRODUCE,
                GROUP_DRINKS
        };

        private static final int[] CATEGORY_BY_SUBTYPE = new int[] {
                CAT_RED,
                CAT_YELLOW,
                CAT_YELLOW,
                CAT_YELLOW,
                CAT_YELLOW,
                CAT_GREEN,
                CAT_GREEN,
                CAT_GREEN,
                CAT_GREEN
        };

        private static final int[] GROUP_BY_POSITION = new int[] {
                GROUP_EXTRAS,
                GROUP_OILS_FATS, GROUP_OILS_FATS, GROUP_NUTS_SEEDS,
                GROUP_MILK_DAIRY, GROUP_MILK_DAIRY, GROUP_PROTEIN,
                GROUP_GRAINS_SIDES, GROUP_GRAINS_SIDES, GROUP_GRAINS_SIDES, GROUP_GRAINS_SIDES,
                GROUP_PRODUCE, GROUP_PRODUCE, GROUP_PRODUCE, GROUP_PRODUCE, GROUP_PRODUCE,
                GROUP_DRINKS, GROUP_DRINKS, GROUP_DRINKS, GROUP_DRINKS, GROUP_DRINKS, GROUP_DRINKS
        };

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SharedPreferences prefs;
        private final SharedPreferences uiPrefs;
        private final CompanionRepository companionRepository;
        private final CompanionBackupManager companionBackupManager;
        private CompanionUiState.ViewState companionUiState;
        private final CompanionPageState companionPageState = new CompanionPageState();
        private final Runnable companionPageRedrawRunnable = this::invalidate;
        private final float companionPosePixelScale;
        private final List<TouchTarget> targets = new ArrayList<>();
        private final Map<Integer, Boolean> expandedStats = new HashMap<>();

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect bitmapSrc = new Rect();
        private final Rect companionArtworkIdleSrcBounds = new Rect();
        private final Rect companionArtworkHappySrcBounds = new Rect();
        private final Rect companionArtworkEatingSrcBounds = new Rect();
        private final Rect companionArtworkCuddleSrcBounds = new Rect();
        private final Rect companionArtworkSleepingSrcBounds = new Rect();
        private final Rect companionTreatBowlSrcBounds = new Rect();
        private final Rect companionPlayBallSrcBounds = new Rect();
        private final Rect companionCuddleHeartSrcBounds = new Rect();
        private final Rect companionEmptySrcBounds = new Rect();
        private final boolean[] companionPoseBoundsCaptured =
                new boolean[CompanionPageState.Pose.values().length];
        private boolean companionTreatBowlBoundsCaptured;
        private boolean companionPlayBallBoundsCaptured;
        private boolean companionCuddleHeartBoundsCaptured;
        private final Path companionSceneClipPath = new Path();
        private final RectF companionSceneImageBounds = new RectF();
        private final RectF companionLambDrawBounds = new RectF();
        private final RectF companionTreatBowlDrawBounds = new RectF();
        private final RectF companionPlayBallDrawBounds = new RectF();
        private final RectF companionCuddleHeartDrawBounds = new RectF();
        private Bitmap foodDessertBmp, foodOilBmp, foodButterBmp, foodMilkCheeseBmp,
                foodProteinBmp, foodWheatBmp, foodWheatPotatoBmp, foodProduceBmp,
                foodWaterBmp, foodNutsSeedsBmp, aboutBrandBmp, appSymbolBmp,
                companionArtworkIdleBmp, companionArtworkHappyBmp,
                companionArtworkEatingBmp, companionArtworkCuddleBmp,
                companionArtworkSleepingBmp, companionBackdropDayBmp,
                companionBackdropNightBmp, companionBarnLayerDayBmp,
                companionBarnLayerNightBmp, companionTreatBowlBmp,
                companionPlayBallBmp, companionCuddleHeartBmp, companionTokenFlowerBmp;

        private int activeTab = TAB_TODAY;
        private int themeMode;
        private int skinStyle;
        private boolean dark;
        private boolean settingsOpen = false;
        private boolean companionBackupMenuOpen = false;
        private boolean aboutOpen = false;
        private boolean changelogOpen = false;
        private boolean licenseOpen = false;
        private boolean portionHelpOpen = false;
        private boolean backupOptionsOpen = false;
        private boolean restoreWarningOpen = false;
        private boolean firstLaunchNoticeOpen;
        private boolean licenseOpenedFromFirstLaunch = false;
        private String blockingDataErrorTitle = null;
        private String blockingDataErrorMessage = null;
        private BackupData pendingRestoreBackup = null;
        private LocalDate selectedDate = LocalDate.now();
        private LocalDate overviewMonth = LocalDate.now().withDayOfMonth(1);
        private boolean[] ticks = new boolean[BASE_TILE_COUNT];
        private int[] subtypeExtras = new int[PyramidScheme.SUBTYPE_COUNT];
        private String snackbarText = null;
        private long snackbarUntil = 0L;
        private float portionsScrollY = 0f;
        private float portionsMaxScrollY = 0f;
        private final RectF portionsScrollArea = new RectF();
        private float licenseScrollY = 0f;
        private float licenseMaxScrollY = 0f;
        private final RectF licenseScrollArea = new RectF();
        private float settingsScrollY = 0f;
        private float settingsMaxScrollY = 0f;
        private final RectF settingsScrollArea = new RectF();
        private float settingsLastBodyW = -1f;
        private float settingsLastBodyH = -1f;
        private float settingsLastContentH = -1f;
        private int settingsOpenMenu = SETTINGS_MENU_NONE;
        private int settingsPressedMenu = SETTINGS_MENU_NONE;
        private final RectF settingsLanguageSelectorRect = new RectF();
        private final RectF settingsThemeSelectorRect = new RectF();
        private boolean settingsBodyTouchActive = false;
        private boolean settingsBodyTouchMoved = false;
        private float settingsBodyTouchDownX = 0f;
        private float settingsBodyTouchDownY = 0f;
        private float portionInfoScrollY = 0f;
        private float portionInfoMaxScrollY = 0f;
        private final RectF portionInfoScrollArea = new RectF();
        private float portionInfoLastBodyW = -1f;
        private float portionInfoLastBodyH = -1f;
        private float portionInfoLastContentH = -1f;
        private float firstLaunchScrollY = 0f;
        private float firstLaunchMaxScrollY = 0f;
        private final RectF firstLaunchScrollArea = new RectF();
        private int overlayScrollKind = OVERLAY_SCROLL_NONE;
        private boolean overlayScrollDragging = false;
        private float overlayTouchDownY = 0f;
        private float overlayScrollStartY = 0f;
        private boolean portionsTouchActive = false;
        private boolean portionsDragging = false;
        private float portionsTouchDownY = 0f;
        private float portionsScrollStartY = 0f;
        private float statsScrollY = 0f;
        private float statsMaxScrollY = 0f;
        private final RectF statsScrollArea = new RectF();
        private boolean statsTouchActive = false;
        private boolean statsDragging = false;
        private float statsTouchDownY = 0f;
        private float statsScrollStartY = 0f;

        private int bg, card, primaryText, secondaryText, accent, outline;
        private int red, yellow, green, waterBlue;

        EscaView(Context context) {
            super(context);
            companionPosePixelScale = dp(COMPANION_POSE_CANVAS_DP) / COMPANION_POSE_CANVAS_PX;
            prefs = context.getSharedPreferences(
                    PyramidScheme.PREFERENCES_NAME,
                    Context.MODE_PRIVATE);
            uiPrefs = context.getSharedPreferences(
                    AppLanguage.PREFERENCES_NAME,
                    Context.MODE_PRIVATE);
            companionRepository = CompanionRepository.create(context);
            companionBackupManager = CompanionBackupManager.create(context);
            companionUiState = loadCompanionUiState();
            themeMode = prefs.getInt("theme_mode", MODE_SYSTEM);
            skinStyle = prefs.getInt("skin_style", SKIN_PASTEL_COZY);
            selectedDate = readSelectedDate();
            overviewMonth = selectedDate.withDayOfMonth(1);
            firstLaunchNoticeOpen = prefs.getInt(FIRST_LAUNCH_ACK_VERSION_KEY, 0)
                    < FIRST_LAUNCH_NOTICE_VERSION;
            text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            loadFoodBitmaps();
            setFocusable(true);
            loadDay(selectedDate);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            targets.clear();
            updatePalette();
            canvas.drawColor(bg);

            float w = getWidth();
            float h = getHeight();
            if (blockingDataErrorMessage != null) {
                drawMigrationError(canvas, w, h);
                return;
            }
            float margin = dp(18);
            float y = dp(34);

            drawHeader(canvas, margin, y, w - margin);
            y += activeTab == TAB_PORTIONS ? dp(52) : dp(70);

            float bottomInset = systemBottomInset();
            float navHeight = dp(54);
            float navTop = h - bottomInset - navHeight;

            if (activeTab == TAB_TODAY) {
                drawDateStrip(canvas, margin, y, w - margin);
                y += dp(104);
                drawToday(canvas, margin, y, w - margin, navTop - dp(12));
            } else if (activeTab == TAB_OVERVIEW) {
                drawOverview(canvas, margin, y, w - margin, navTop - dp(12));
            } else if (activeTab == TAB_STATS) {
                drawStats(canvas, margin, y, w - margin, navTop - dp(12));
            } else {
                drawPortionsScreen(canvas, margin, y, w - margin, navTop - dp(12));
            }

            drawBottomNav(canvas, w, h, bottomInset);
            if (settingsOpen) drawSettings(canvas, w, h);
            if (aboutOpen) drawAboutOverlay(canvas, w, h);
            if (licenseOpen && !licenseOpenedFromFirstLaunch) drawLicenseOverlay(canvas, w, h);
            if (portionHelpOpen) drawPortionHelpOverlay(canvas, w, h);
            if (backupOptionsOpen) drawBackupOptionsOverlay(canvas, w, h);
            if (restoreWarningOpen) drawRestoreWarningOverlay(canvas, w, h);
            if (companionBackupMenuOpen) drawCompanionBackupMenu(canvas, w, h);
            if (companionPageState.isOpen()) drawCompanionPage(canvas, w, h);
            drawSnackbar(canvas, w, h - bottomInset);
            if (firstLaunchNoticeOpen) {
                targets.clear();
                drawFirstLaunchNotice(canvas, w, h);
                if (licenseOpenedFromFirstLaunch && licenseOpen) {
                    targets.clear();
                    drawLicenseOverlay(canvas, w, h);
                }
            }
        }
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (blockingDataErrorMessage != null) return true;
            int action = event.getActionMasked();
            float x = event.getX();
            float y = event.getY();

            updateSettingsBodyTouchState(action, x, y);
            if (handleOverlayScroll(action, x, y)) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    clearSettingsBodyTouchState();
                }
                return true;
            }

            boolean overlayOpen = settingsOpen || companionPageState.isOpen()
                    || companionBackupMenuOpen || aboutOpen || licenseOpen || portionHelpOpen || backupOptionsOpen
                    || restoreWarningOpen || firstLaunchNoticeOpen;
            boolean canScrollPortions = activeTab == TAB_PORTIONS && !overlayOpen && portionsMaxScrollY > 0f;
            boolean canScrollStats = activeTab == TAB_STATS && !overlayOpen && statsMaxScrollY > 0f;

            if (canScrollStats) {
                if (action == MotionEvent.ACTION_DOWN) {
                    statsTouchActive = statsScrollArea.contains(x, y);
                    statsDragging = false;
                    statsTouchDownY = y;
                    statsScrollStartY = statsScrollY;
                    return true;
                }

                if (action == MotionEvent.ACTION_MOVE && statsTouchActive) {
                    float dy = y - statsTouchDownY;
                    if (Math.abs(dy) > dp(4) || statsDragging) {
                        statsDragging = true;
                        statsScrollY = Math.max(0f, Math.min(statsMaxScrollY, statsScrollStartY - dy));
                        invalidate();
                        return true;
                    }
                }

                if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) && statsDragging) {
                    statsTouchActive = false;
                    statsDragging = false;
                    return true;
                }
            }

            if (canScrollPortions) {
                if (action == MotionEvent.ACTION_DOWN) {
                    portionsTouchActive = portionsScrollArea.contains(x, y);
                    portionsDragging = false;
                    portionsTouchDownY = y;
                    portionsScrollStartY = portionsScrollY;
                    return true;
                }

                if (action == MotionEvent.ACTION_MOVE && portionsTouchActive) {
                    float dy = y - portionsTouchDownY;
                    if (Math.abs(dy) > dp(4) || portionsDragging) {
                        portionsDragging = true;
                        portionsScrollY = Math.max(0f, Math.min(portionsMaxScrollY, portionsScrollStartY - dy));
                        invalidate();
                        return true;
                    }
                }

                if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) && portionsDragging) {
                    portionsTouchActive = false;
                    portionsDragging = false;
                    return true;
                }
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                portionsTouchActive = false;
                portionsDragging = false;
                statsTouchActive = false;
                statsDragging = false;
                clearSettingsBodyTouchState();
                return true;
            }

            if (action != MotionEvent.ACTION_UP) return true;

            portionsTouchActive = false;
            portionsDragging = false;
            statsTouchActive = false;
            statsDragging = false;
            if (shouldConsumeSettingsBodyRelease()) {
                clearSettingsBodyTouchState();
                invalidate();
                return true;
            }
            clearSettingsBodyTouchState();

            for (int i = targets.size() - 1; i >= 0; i--) {
                TouchTarget t = targets.get(i);
                if (t.rect.contains(x, y)) {
                    t.action.run();
                    invalidate();
                    return true;
                }
            }
            return true;
        }

        private boolean handleOverlayScroll(int action, float x, float y) {
            if (action == MotionEvent.ACTION_DOWN) {
                overlayScrollKind = OVERLAY_SCROLL_NONE;
                overlayScrollDragging = false;

                if (licenseOpen && licenseMaxScrollY > 0f && licenseScrollArea.contains(x, y)) {
                    licenseScrollY = clampOverlayScroll(licenseScrollY, licenseMaxScrollY);
                    overlayScrollKind = OVERLAY_SCROLL_LICENSE;
                    overlayTouchDownY = y;
                    overlayScrollStartY = licenseScrollY;
                    return true;
                }

                if (settingsOpen && settingsMaxScrollY > 0f && settingsScrollArea.contains(x, y)) {
                    settingsScrollY = clampOverlayScroll(settingsScrollY, settingsMaxScrollY);
                    overlayScrollKind = OVERLAY_SCROLL_SETTINGS;
                    overlayTouchDownY = y;
                    overlayScrollStartY = settingsScrollY;
                    return true;
                }

                if (firstLaunchNoticeOpen && !licenseOpenedFromFirstLaunch
                        && firstLaunchMaxScrollY > 0f && firstLaunchScrollArea.contains(x, y)) {
                    firstLaunchScrollY = clampOverlayScroll(firstLaunchScrollY, firstLaunchMaxScrollY);
                    overlayScrollKind = OVERLAY_SCROLL_FIRST_LAUNCH;
                    overlayTouchDownY = y;
                    overlayScrollStartY = firstLaunchScrollY;
                    return true;
                }

                if (portionHelpOpen && portionInfoMaxScrollY > 0f && portionInfoScrollArea.contains(x, y)) {
                    portionInfoScrollY = clampOverlayScroll(portionInfoScrollY, portionInfoMaxScrollY);
                    overlayScrollKind = OVERLAY_SCROLL_PORTION_INFO;
                    overlayTouchDownY = y;
                    overlayScrollStartY = portionInfoScrollY;
                    return true;
                }
            }

            if (action == MotionEvent.ACTION_MOVE && overlayScrollKind != OVERLAY_SCROLL_NONE) {
                float dy = y - overlayTouchDownY;
                if (Math.abs(dy) > dp(4) || overlayScrollDragging) {
                    overlayScrollDragging = true;
                    boolean changed;
                    if (overlayScrollKind == OVERLAY_SCROLL_LICENSE) {
                        float next = clampOverlayScroll(overlayScrollStartY - dy, licenseMaxScrollY);
                        changed = next != licenseScrollY;
                        licenseScrollY = next;
                    } else if (overlayScrollKind == OVERLAY_SCROLL_SETTINGS) {
                        float next = clampOverlayScroll(overlayScrollStartY - dy, settingsMaxScrollY);
                        changed = next != settingsScrollY;
                        settingsScrollY = next;
                    } else if (overlayScrollKind == OVERLAY_SCROLL_PORTION_INFO) {
                        float next = clampOverlayScroll(overlayScrollStartY - dy, portionInfoMaxScrollY);
                        changed = next != portionInfoScrollY;
                        portionInfoScrollY = next;
                    } else {
                        float next = clampOverlayScroll(overlayScrollStartY - dy, firstLaunchMaxScrollY);
                        changed = next != firstLaunchScrollY;
                        firstLaunchScrollY = next;
                    }
                    if (changed) invalidate();
                    return true;
                }
                return true;
            }

            if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP)
                    && overlayScrollKind != OVERLAY_SCROLL_NONE) {
                int completedKind = overlayScrollKind;
                boolean consume = overlayScrollDragging
                        || completedKind != OVERLAY_SCROLL_SETTINGS;
                overlayScrollKind = OVERLAY_SCROLL_NONE;
                overlayScrollDragging = false;
                return consume;
            }

            return false;
        }

        private float clampOverlayScroll(float value, float maxScroll) {
            if (maxScroll <= 0f) return 0f;
            return Math.max(0f, Math.min(maxScroll, value));
        }

        private void updateSettingsBodyTouchState(int action, float x, float y) {
            if (!settingsOpen) {
                clearSettingsBodyTouchState();
                return;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                settingsBodyTouchActive = settingsScrollArea.contains(x, y);
                settingsBodyTouchMoved = false;
                settingsBodyTouchDownX = x;
                settingsBodyTouchDownY = y;
                setSettingsPressedMenu(settingsBodyTouchActive ? settingsMenuAt(x, y) : SETTINGS_MENU_NONE);
                return;
            }
            if (action == MotionEvent.ACTION_MOVE && settingsBodyTouchActive) {
                float dx = x - settingsBodyTouchDownX;
                float dy = y - settingsBodyTouchDownY;
                if (Math.hypot(dx, dy) > dp(4)) {
                    settingsBodyTouchMoved = true;
                    setSettingsPressedMenu(SETTINGS_MENU_NONE);
                }
                if (settingsPressedMenu != SETTINGS_MENU_NONE
                        && !settingsSelectorRect(settingsPressedMenu).contains(x, y)) {
                    setSettingsPressedMenu(SETTINGS_MENU_NONE);
                }
                return;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                setSettingsPressedMenu(SETTINGS_MENU_NONE);
            }
        }

        private boolean shouldConsumeSettingsBodyRelease() {
            return settingsOpen && settingsBodyTouchActive && settingsBodyTouchMoved;
        }

        private void clearSettingsBodyTouchState() {
            settingsBodyTouchActive = false;
            settingsBodyTouchMoved = false;
            settingsBodyTouchDownX = 0f;
            settingsBodyTouchDownY = 0f;
            setSettingsPressedMenu(SETTINGS_MENU_NONE);
        }

        private void setSettingsPressedMenu(int menuId) {
            if (settingsPressedMenu == menuId) return;
            settingsPressedMenu = menuId;
            invalidate();
        }

        private int settingsMenuAt(float x, float y) {
            if (settingsLanguageSelectorRect.contains(x, y)) return SETTINGS_MENU_LANGUAGE;
            if (settingsThemeSelectorRect.contains(x, y)) return SETTINGS_MENU_THEME;
            return SETTINGS_MENU_NONE;
        }

        private RectF settingsSelectorRect(int menuId) {
            if (menuId == SETTINGS_MENU_LANGUAGE) return settingsLanguageSelectorRect;
            if (menuId == SETTINGS_MENU_THEME) return settingsThemeSelectorRect;
            return new RectF();
        }

        private void drawHeader(Canvas c, float left, float top, float right) {
            boolean cozy = isPastelCozy();
            float iconSize = dp(46);
            RectF aboutIcon = startRect(left, right, dp(0), iconSize, top + dp(3), iconSize);

            float settingsSize = dp(40);
            RectF settings = endRect(left, right, dp(0), settingsSize, top + dp(5), settingsSize);

            float textStart = isRtl() ? aboutIcon.left - dp(12) : aboutIcon.right + dp(12);
            float textEnd = isRtl() ? settings.right + dp(12) : settings.left - dp(12);

            if (appSymbolBmp != null) {
                drawRoundedBitmapIcon(c, appSymbolBmp, aboutIcon, dp(12));
            } else {
                drawLambBeeAvatar(c, aboutIcon);
            }
            targets.add(new TouchTarget(aboutIcon, this::openAboutOverlayFromSettings));
            if (activeTab == TAB_PORTIONS) {
                String headerTitle = s(R.string.nav_portions);
                text.setTextAlign(Paint.Align.CENTER);
                text.setFakeBoldText(true);
                float titleSize = cozy ? dp(27) : dp(28);
                text.setTextSize(titleSize);
                float titleMaxW = Math.max(dp(150), Math.abs(settings.centerX() - aboutIcon.centerX()) - dp(24));
                while (text.measureText(headerTitle) > titleMaxW && titleSize > dp(18)) {
                    titleSize -= dp(1);
                    text.setTextSize(titleSize);
                }
                text.setColor(accent);
                float titleCenter = (left + right) / 2f;
                float titleBaseline = top + dp(35);
                c.drawText(headerTitle, titleCenter, titleBaseline, text);
                float titleWidth = text.measureText(headerTitle);
                Paint.FontMetrics titleMetrics = text.getFontMetrics();
                text.setFakeBoldText(false);

                float infoTargetSize = dp(44);
                float infoCx = isRtl()
                        ? Math.max(
                                titleCenter - titleWidth / 2f - dp(24),
                                settings.right + dp(22))
                        : Math.min(
                                titleCenter + titleWidth / 2f + dp(24),
                                settings.left - dp(22));
                float infoCy = titleBaseline + (titleMetrics.ascent + titleMetrics.descent) / 2f;
                RectF info = new RectF(infoCx - infoTargetSize / 2f, infoCy - infoTargetSize / 2f,
                        infoCx + infoTargetSize / 2f, infoCy + infoTargetSize / 2f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(cozy ? (dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 125)) : alpha(accent, dark ? 38 : 22));
                c.drawCircle(info.centerX(), info.centerY(), dp(14), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(cozy ? (dark ? Color.rgb(148, 127, 160) : Color.rgb(221, 203, 176)) : outline);
                c.drawCircle(info.centerX(), info.centerY(), dp(14), p);
                text.setTextAlign(Paint.Align.CENTER);
                text.setFakeBoldText(true);
                text.setTextSize(dp(13));
                text.setColor(accent);
                c.drawText(s(R.string.symbol_info), info.centerX(), info.centerY() + dp(5), text);
                text.setFakeBoldText(false);
                targets.add(new TouchTarget(info, () -> {
                    portionHelpOpen = true;
                    portionInfoScrollY = 0f;
                    portionInfoMaxScrollY = 0f;
                    portionInfoLastBodyW = -1f;
                    portionInfoLastBodyH = -1f;
                    portionInfoLastContentH = -1f;
                    closeSettingsOverlay();
                    aboutOpen = false;
                    changelogOpen = false;
                    licenseOpen = false;
                    backupOptionsOpen = false;
                    restoreWarningOpen = false;
                }));
            } else {
                String headerTitle = s(R.string.header_pyramid);
                text.setTextAlign(startAlign());
                text.setFakeBoldText(true);
                float titleSize = cozy ? dp(25) : dp(26);
                text.setTextSize(titleSize);
                while (text.measureText(headerTitle) > Math.abs(textEnd - textStart) && titleSize > dp(17)) {
                    titleSize -= dp(1);
                    text.setTextSize(titleSize);
                }
                text.setColor(accent);
                text.setTextAlign(startAlign());
                c.drawText(headerTitle, textStart, top + dp(29), text);
                text.setFakeBoldText(false);

                float subSize = dp(13);
                text.setTextSize(subSize);
                String subtitle = s(R.string.header_subtitle);
                while (text.measureText(subtitle) > Math.abs(textEnd - textStart) && subSize > dp(10)) {
                    subSize -= dp(1);
                    text.setTextSize(subSize);
                }
                text.setColor(secondaryText);
                c.drawText(BidiFormatter.getInstance(isRtl()).unicodeWrap(subtitle), textStart, top + dp(52), text);
                if (cozy) drawLeafSprig(c, textStart + (isRtl() ? -dp(2) : dp(2)), top + dp(60), dp(15), accent);
            }

            int settingsFill = cozy ? (dark ? Color.rgb(61, 53, 67) : Color.rgb(255, 249, 238)) : alpha(accent, dark ? 44 : 24);
            int settingsStroke = cozy ? (dark ? Color.rgb(148, 127, 160) : Color.rgb(221, 203, 176)) : outline;
            int settingsIcon = cozy ? (dark ? Color.rgb(222, 245, 205) : Color.rgb(104, 154, 112)) : accent;

            p.setStyle(Paint.Style.FILL);
            p.setColor(settingsFill);
            c.drawRoundRect(settings, dp(11), dp(11), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(settingsStroke);
            c.drawRoundRect(settings, dp(11), dp(11), p);
            drawSettingsSlidersIcon(c, settings, settingsIcon);
            targets.add(new TouchTarget(settings, this::openSettingsOverlay));
        }

        private void drawDateStrip(Canvas c, float left, float top, float right) {
            RectF r = new RectF(left, top, right, top + dp(86));
            drawCard(c, r);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(17));
            text.setColor(accent);
            c.drawText(dateHeader(selectedDate), r.centerX(), top + dp(27), text);
            text.setFakeBoldText(false);

            RectF prev = startRect(left, right, dp(8), dp(40), top + dp(8), dp(40));
            RectF next = endRect(left, right, dp(8), dp(40), top + dp(8), dp(40));
            drawChevron(c, prev, !isRtl(), accent);
            drawChevron(c, next, isRtl(), accent);
            targets.add(new TouchTarget(prev, () -> changeSelectedDate(selectedDate.minusDays(1))));
            targets.add(new TouchTarget(next, () -> changeSelectedDate(selectedDate.plusDays(1))));

            LocalDate start = selectedDate.minusDays(3);
            float cellW = (r.width() - dp(32)) / 7f;
            float x = left + dp(16);
            for (int i = 0; i < 7; i++) {
                final LocalDate day = start.plusDays(i);
                float cellLeft = isRtl()
                        ? right - dp(16) - (i + 1) * cellW
                        : x + i * cellW;
                RectF cell = new RectF(cellLeft, top + dp(37), cellLeft + cellW, top + dp(80));
                boolean isSelected = day.equals(selectedDate);
                if (isSelected) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(accent);
                    c.drawRoundRect(inset(cell, dp(5), dp(1)), dp(13), dp(13), p);
                }
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(dp(11));
                text.setColor(isSelected ? Color.WHITE : secondaryText);
                c.drawText(AppLanguage.compactWeekdayLabel(day.getDayOfWeek(), appLocale()), cell.centerX(), cell.top + dp(15), text);
                text.setTextSize(dp(14));
                text.setFakeBoldText(true);
                text.setColor(isSelected ? Color.WHITE : primaryText);
                c.drawText(String.valueOf(day.getDayOfMonth()), cell.centerX(), cell.top + dp(32), text);
                text.setFakeBoldText(false);
                p.setStyle(Paint.Style.FILL);
                p.setColor(isSelected ? Color.WHITE : alpha(accent, 105));
                c.drawCircle(cell.centerX(), cell.bottom - dp(5), dp(2.1f), p);
                targets.add(new TouchTarget(cell, () -> changeSelectedDate(day)));
            }
        }

        private void drawToday(Canvas c, float left, float top, float right, float bottom) {
            float desired = isPastelCozy() ? dp(406) : dp(388);
            float messageGap = dp(12);
            float messageHeight = dp(74);
            float minMessageHeight = dp(58);
            float availableHeight = bottom - top;

            float cardBottom = top + desired;
            if (availableHeight >= desired + messageGap + messageHeight) {
                cardBottom = top + desired;
            } else if (availableHeight >= dp(330) + messageGap + minMessageHeight) {
                cardBottom = bottom - messageGap - minMessageHeight;
            }

            RectF cardRect = new RectF(left, top, right, Math.min(bottom, cardBottom));
            drawCard(c, cardRect);
            drawPyramid(c, cardRect.left + dp(12), cardRect.top + dp(16), cardRect.right - dp(12));

            float messageTop = cardRect.bottom + messageGap;
            float messageBottom = Math.min(bottom, messageTop + messageHeight);
            if (messageBottom - messageTop >= minMessageHeight) {
                CompanionUiState.ViewState companion = companionUiState;
                boolean showCompanion = companion.available
                        && companion.presentation.enabled;
                RectF motivation = new RectF(left, messageTop, right, messageBottom);
                drawMotivation(c, motivation, showCompanion);
                if (showCompanion) {
                    float previewWidth = Math.min(dp(92), motivation.width() * .31f);
                    RectF preview = new RectF(
                            motivation.right - previewWidth - dp(7),
                            motivation.top + dp(7),
                            motivation.right - dp(7),
                            motivation.bottom - dp(7));
                    drawCompanionPreview(c, preview, companion.presentation);
                    targets.add(new TouchTarget(preview, this::openCompanionPage));
                }
            }
        }

        private void drawPyramid(Canvas c, float left, float top, float right) {
            float availableW = right - left;
            float y = top;
            for (int row = 0; row < ROWS.length; row++) {
                RowDef def = ROWS[row];
                boolean mixedRow = def.subtypes.length > 1;
                int totalVisible = 0;
                for (int subtype : def.subtypes) {
                    PyramidInteractionRules.ExtraDisplay display = mixedRow
                            ? PyramidScheme.mixedSubtypeExtraDisplay(subtypeExtras[subtype])
                            : PyramidInteractionRules.extraDisplay(
                                    subtypeExtras[subtype],
                                    MAX_VISIBLE_EXTRAS);
                    totalVisible += PyramidScheme.SUBTYPE_DEFAULT_COUNTS[subtype]
                            + display.visible
                            + (PyramidScheme.isSubtypeComplete(ticks, subtype) ? 1 : 0);
                }

                float gap = gapForVisibleCount(totalVisible);
                float tile = tileForVisibleCount(availableW, totalVisible, gap);

                float groupW = PyramidInteractionRules.rowWidth(totalVisible, tile, gap);
                float x = left + (availableW - groupW) / 2f;

                for (int subtype : def.subtypes) {
                    int defaultStart = PyramidScheme.SUBTYPE_STARTS[subtype];
                    int defaultCount = PyramidScheme.SUBTYPE_DEFAULT_COUNTS[subtype];
                    int type = typeForSubtype(subtype);
                    for (int i = 0; i < defaultCount; i++) {
                        final int tileIndex = defaultStart + i;
                        RectF r = new RectF(x, y, x + tile, y + tile);
                        drawFoodTile(
                                c,
                                r,
                                visualCategoryForSubtype(subtype),
                                typeForPosition(tileIndex),
                                ticks[tileIndex]);
                        targets.add(new TouchTarget(r, () -> handleDefaultTileTap(tileIndex)));
                        x += tile + gap;
                    }

                    PyramidInteractionRules.ExtraDisplay display = mixedRow
                            ? PyramidScheme.mixedSubtypeExtraDisplay(subtypeExtras[subtype])
                            : PyramidInteractionRules.extraDisplay(
                                    subtypeExtras[subtype],
                                    MAX_VISIBLE_EXTRAS);
                    for (int i = 0; i < display.visible; i++) {
                        RectF r = new RectF(x, y, x + tile, y + tile);
                        drawFoodTile(c, r, visualCategoryForSubtype(subtype), type, true);
                        if (i == display.visible - 1 && display.overflow > 0) {
                            drawOverflowBadge(c, r, display.overflow);
                        }
                        final int extraSubtype = subtype;
                        targets.add(new TouchTarget(r, () -> removeSubtypeExtra(extraSubtype)));
                        x += tile + gap;
                    }

                    if (PyramidScheme.isSubtypeComplete(ticks, subtype)) {
                        RectF plus = new RectF(x, y, x + tile, y + tile);
                        final int plusSubtype = subtype;
                        drawPlusTile(c, plus, visualCategoryForSubtype(subtype), 0);
                        targets.add(new TouchTarget(plus, () -> addSubtypeExtra(plusSubtype)));
                        x += tile + gap;
                    }
                }

                y += tile + dp(8);
            }
        }

        private float gapForVisibleCount(int totalVisible) {
            return PyramidInteractionRules.gapForVisibleCount(
                    totalVisible,
                    getResources().getDisplayMetrics().density);
        }

        private float tileForVisibleCount(float availableW, int totalVisible, float gap) {
            return PyramidInteractionRules.tileForVisibleCount(
                    availableW,
                    totalVisible,
                    gap,
                    getResources().getDisplayMetrics().density);
        }



        private void drawFoodTile(Canvas c, RectF r, int category, int type, boolean completed) {
            int base = categoryColor(category);
            int fill = completed ? desaturate(base, 0.22f, dark ? 0.72f : 1.18f) : base;
            float radius = isPastelCozy() ? dp(11) : dp(12);

            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            c.drawRoundRect(r, radius, radius, p);

            if (isPastelCozy()) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(alpha(Color.WHITE, 78));
                c.drawRoundRect(new RectF(r.left + dp(3), r.top + dp(3), r.right - dp(3), r.top + r.height() * .36f), dp(9), dp(9), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(0.75f));
                p.setColor(alpha(categoryBorder(category), 105));
                c.drawRoundRect(inset(r, dp(1.4f), dp(1.4f)), radius - dp(1), radius - dp(1), p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(alpha(Color.WHITE, dark ? 10 : 52));
                c.drawRoundRect(new RectF(r.left + dp(3), r.top + dp(3), r.right - dp(3), r.top + r.height() * .38f), dp(10), dp(10), p);
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setColor(completed ? alpha(primaryText, 80) : alpha(categoryBorder(category), isPastelCozy() ? 130 : 170));
            c.drawRoundRect(r, radius, radius, p);

            drawFoodIcon(c, r, type, completed);
            if (completed) drawX(c, r);
        }

        private void drawPlusTile(Canvas c, RectF r, int category, int badgeCount) {
            int col = categoryColor(category);
            p.setStyle(Paint.Style.FILL);
            p.setColor(isPastelCozy() ? alpha(Color.WHITE, 150) : (dark ? alpha(col, 28) : Color.WHITE));
            c.drawRoundRect(r, dp(12), dp(12), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(isPastelCozy() ? 1.1f : 1.4f));
            p.setColor(alpha(categoryBorder(category), isPastelCozy() ? 160 : 210));
            c.drawRoundRect(r, dp(12), dp(12), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            float plusSize = Math.min(dp(26), r.height() * .62f);
            text.setTextSize(plusSize);
            text.setColor(categoryBorder(category));
            c.drawText("+", r.centerX(), r.centerY() + plusSize * .34f, text);
            text.setFakeBoldText(false);

            if (badgeCount > 0) drawOverflowBadge(c, r, badgeCount);
        }

        private void drawOverflowBadge(Canvas c, RectF anchor, int count) {
            if (count <= 0) return;
            float rad = Math.min(dp(10), Math.max(dp(6), anchor.width() * .28f));
            float cx = anchor.right - rad;
            float cy = anchor.top + rad;
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? Color.rgb(245, 143, 105) : Color.rgb(226, 94, 77));
            c.drawCircle(cx, cy, rad, p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(Math.min(dp(10), rad * .95f));
            text.setColor(Color.WHITE);
            c.drawText("+" + count, cx, cy + rad * .36f, text);
            text.setFakeBoldText(false);
        }

        private void drawFoodIcon(Canvas c, RectF r, int type, boolean muted) {
            RectF inner = inset(r, r.width() * 0.12f, r.height() * 0.12f);
            Bitmap bmp = bitmapForType(type);
            if (bmp != null) {
                drawBitmapIcon(c, bmp, inner, muted);
                return;
            }
            switch (type) {
                case TYPE_DESSERT: drawDessert(c, inner, muted); break;
                case TYPE_OIL: drawOil(c, inner, muted); break;
                case TYPE_BUTTER: drawButter(c, inner, muted); break;
                case TYPE_MILK_CHEESE: drawMilkCheese(c, inner, muted); break;
                case TYPE_PROTEIN: drawProteinGroup(c, inner, muted); break;
                case TYPE_WHEAT: drawWheat(c, inner, muted); break;
                case TYPE_WHEAT_POTATO: drawWheatPotato(c, inner, muted); break;
                case TYPE_PRODUCE: drawApple(c, inner, muted); break;
                case TYPE_WATER: drawWater(c, inner, muted); break;
                case TYPE_NUTS: drawNutsSeeds(c, inner, muted); break;
                default: drawExtraIcon(c, inner, muted); break;
            }
        }

        private void drawOverview(Canvas c, float left, float top, float right, float bottom) {
            RectF cardRect = new RectF(left, top, right, bottom);
            drawCard(c, cardRect);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(18));
            text.setColor(accent);
            c.drawText(monthHeader(overviewMonth), cardRect.centerX(), top + dp(34), text);
            text.setFakeBoldText(false);

            RectF prev = startRect(left, right, dp(10), dp(38), top + dp(11), dp(38));
            RectF next = endRect(left, right, dp(10), dp(38), top + dp(11), dp(38));
            drawChevron(c, prev, !isRtl(), accent);
            drawChevron(c, next, isRtl(), accent);
            targets.add(new TouchTarget(prev, () -> overviewMonth = overviewMonth.minusMonths(1)));
            targets.add(new TouchTarget(next, () -> overviewMonth = overviewMonth.plusMonths(1)));

            float weekY = top + dp(64);
            float gridLeft = left + dp(14);
            float gridRight = right - dp(14);
            float cellW = (gridRight - gridLeft) / 7f;
            text.setTextSize(dp(11));
            text.setColor(secondaryText);
            LocalDate weekStart = LocalDate.of(2024, 1, 1);
            for (int i = 0; i < 7; i++) {
                String dayLabel = AppLanguage.compactWeekdayLabel(
                        weekStart.plusDays(i).getDayOfWeek(), appLocale());
                c.drawText(dayLabel, calendarCellCenter(gridLeft, gridRight, cellW, i), weekY, text);
            }

            LocalDate first = overviewMonth;
            int offset = first.getDayOfWeek().getValue() - 1;
            LocalDate cursor = first.minusDays(offset);
            float cellH = Math.min(dp(54), (bottom - (top + dp(88))) / 6f);
            float y = top + dp(76);
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 7; col++) {
                    LocalDate d = cursor.plusDays(row * 7L + col);
                    float cellLeft = calendarCellLeft(gridLeft, gridRight, cellW, col) + dp(2);
                    RectF cell = new RectF(cellLeft, y + row * cellH, cellLeft + cellW - dp(4), y + (row + 1) * cellH - dp(3));
                    boolean inMonth = d.getMonth().equals(overviewMonth.getMonth());
                    boolean selected = d.equals(selectedDate);
                    if (selected) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(alpha(accent, dark ? 80 : 52));
                        c.drawRoundRect(cell, dp(10), dp(10), p);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(dp(1.3f));
                        p.setColor(accent);
                        c.drawRoundRect(cell, dp(10), dp(10), p);
                    }
                    text.setTextSize(dp(12));
                    text.setColor(inMonth ? primaryText : alpha(secondaryText, 80));
                    c.drawText(String.valueOf(d.getDayOfMonth()), cell.centerX(), cell.top + dp(15), text);
                    DayData dd = readDay(d);
                    int[] used = colorCountsForDay(dd, false);
                    float chipY = cell.top + dp(29);
                    drawMiniCount(c, cell.centerX() - dp(13), chipY, red, used[CAT_RED]);
                    drawMiniCount(c, cell.centerX(), chipY, yellow, used[CAT_YELLOW]);
                    drawMiniCount(c, cell.centerX() + dp(13), chipY, green, used[CAT_GREEN]);
                    final LocalDate targetDay = d;
                    targets.add(new TouchTarget(cell, () -> openOverviewDate(targetDay)));
                }
            }
        }

        private void drawMiniCount(Canvas c, float cx, float cy, int color, int count) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(count > 0 ? color : alpha(color, 45));
            c.drawCircle(cx, cy, dp(6), p);
            if (count > 0) {
                text.setTextAlign(Paint.Align.CENTER);
                text.setFakeBoldText(true);
                text.setTextSize(dp(7));
                text.setColor(dark ? Color.rgb(36, 35, 34) : Color.WHITE);
                c.drawText(String.valueOf(Math.min(9, count)), cx, cy + dp(2.5f), text);
                text.setFakeBoldText(false);
            }
        }

        private void drawStats(Canvas c, float left, float top, float right, float bottom) {
            RectF cardRect = new RectF(left, top, right, bottom);
            drawCard(c, cardRect);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(18));
            text.setColor(accent);
            c.drawText(s(R.string.nav_statistics), cardRect.centerX(), top + dp(32), text);
            text.setFakeBoldText(false);

            RectF viewport = new RectF(left + dp(14), top + dp(48), right - dp(14), bottom - dp(10));
            statsScrollArea.set(viewport);
            String[] titles = {
                    s(R.string.stats_this_week),
                    s(R.string.stats_this_month),
                    s(R.string.stats_total)
            };
            float contentHeight = 0f;
            for (int period = 0; period < 3; period++) {
                contentHeight += dp(statPeriodHeight(period));
            }
            contentHeight += dp(8);
            statsMaxScrollY = Math.max(0f, contentHeight - viewport.height());
            statsScrollY = Math.max(0f, Math.min(statsScrollY, statsMaxScrollY));

            int save = c.save();
            c.clipRect(viewport);
            float y = top + dp(56) - statsScrollY;
            for (int period = 0; period < 3; period++) {
                PeriodStats ps = calculatePeriod(period);
                float foodHeight = statFoodSectionHeight(period);
                RectF section = new RectF(left + dp(14), y, right - dp(14), y + dp(foodHeight));
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark ? alpha(Color.WHITE, 15) : alpha(Color.WHITE, 115));
                c.drawRoundRect(section, dp(15), dp(15), p);
                text.setTextAlign(startAlign());
                text.setFakeBoldText(true);
                text.setTextSize(dp(14));
                text.setColor(primaryText);
                c.drawText(titles[period], startX(section, dp(14)), section.top + dp(23), text);
                text.setFakeBoldText(false);

                float boxY = section.top + dp(28);
                float boxW = (section.width() - dp(44)) / 3f;
                drawStatBox(c, new RectF(section.left + dp(12), boxY, section.left + dp(12) + boxW, boxY + dp(34)), CAT_RED, ps.usedByCat[CAT_RED], ps.totalByCat[CAT_RED], period * 10 + CAT_RED);
                drawStatBox(c, new RectF(section.left + dp(22) + boxW, boxY, section.left + dp(22) + boxW * 2, boxY + dp(34)), CAT_YELLOW, ps.usedByCat[CAT_YELLOW], ps.totalByCat[CAT_YELLOW], period * 10 + CAT_YELLOW);
                drawStatBox(c, new RectF(section.left + dp(32) + boxW * 2, boxY, section.left + dp(32) + boxW * 3, boxY + dp(34)), CAT_GREEN, ps.usedByCat[CAT_GREEN], ps.totalByCat[CAT_GREEN], period * 10 + CAT_GREEN);

                float waterTop = section.bottom + dp(6);
                RectF waterSection = new RectF(section.left, waterTop, section.right, waterTop + dp(waterSectionHeight(period)));
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark ? alpha(Color.WHITE, 15) : alpha(Color.WHITE, 115));
                c.drawRoundRect(waterSection, dp(15), dp(15), p);
                text.setTextAlign(startAlign());
                text.setFakeBoldText(true);
                text.setTextSize(dp(13));
                text.setColor(primaryText);
                c.drawText(s(R.string.portions_drinks_title), startX(waterSection, dp(14)), waterSection.top + dp(19), text);
                text.setFakeBoldText(false);
                drawStatBox(c,
                        new RectF(waterSection.left + dp(12), waterSection.top + dp(27), waterSection.right - dp(12), waterSection.top + dp(57)),
                        CAT_WATER,
                        ps.usedByGroup[GROUP_DRINKS],
                        ps.totalByGroup[GROUP_DRINKS],
                        period * 10 + CAT_WATER);

                y += dp(statPeriodHeight(period));
            }
            c.restoreToCount(save);
            drawOverlayScrollIndicator(c, viewport, statsScrollY, statsMaxScrollY);
        }

        private float statFoodSectionHeight(int period) {
            return 66f + (isExpanded(period * 10 + CAT_RED)
                    || isExpanded(period * 10 + CAT_YELLOW)
                    || isExpanded(period * 10 + CAT_GREEN) ? 34f : 0f);
        }

        private float waterSectionHeight(int period) {
            return 60f + (isExpanded(period * 10 + CAT_WATER) ? 34f : 0f);
        }

        private float statPeriodHeight(int period) {
            return statFoodSectionHeight(period) + 6f + waterSectionHeight(period) + 6f;
        }

        private void drawStatBox(Canvas c, RectF r, int cat, int used, int total, int key) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(categoryColor(cat));
            c.drawRoundRect(r, dp(12), dp(12), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.1f));
            p.setColor(categoryBorder(cat));
            c.drawRoundRect(r, dp(12), dp(12), p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(13));
            text.setColor(cat == CAT_WATER && dark
                    ? Color.BLACK
                    : Color.rgb(56, 49, 43));
            c.drawText(used + "/" + total, r.centerX(), r.centerY() + dp(5), text);
            text.setFakeBoldText(false);
            addVisibleTouchTarget(statsScrollArea, r, () -> expandedStats.put(key, !isExpanded(key)));

            if (isExpanded(key)) {
                PeriodStats ps = calculatePeriod(key / 10);
                float y = r.bottom + dp(4);
                int[] groups = groupsForCategory(cat);
                float x = r.left;
                int itemCount = groups.length + 1;
                float tile = Math.min(dp(24), (r.width() - dp(2) * (itemCount - 1))
                        / Math.max(1, itemCount));
                for (int group : groups) {
                    int u = ps.usedByGroup[group];
                    int t = ps.totalByGroup[group];
                    RectF mini = new RectF(x, y, x + tile, y + tile);
                    drawFoodTile(c, mini, cat, groupIconType(group), u > 0);
                    text.setTextAlign(Paint.Align.CENTER);
                    text.setTextSize(dp(7));
                    text.setColor(primaryText);
                    c.drawText(u + "/" + t, mini.centerX(), mini.bottom + dp(8), text);
                    x += tile + dp(2);
                }

                int extraUsed = visualExtraUsed(ps, cat, key / 10);
                RectF extraMini = new RectF(x, y, x + tile, y + tile);
                drawFoodTile(c, extraMini, cat, TYPE_EXTRA, extraUsed > 0);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(dp(7));
                text.setColor(primaryText);
                c.drawText(extraUsed + "/0", extraMini.centerX(), extraMini.bottom + dp(8), text);
            }
        }

        private int visualExtraUsed(PeriodStats ps, int cat, int period) {
            if (cat == CAT_WATER) return periodDrinkExtras(period);
            return ps.usedExtraByCat[cat];
        }

        private int periodDrinkExtras(int period) {
            LocalDate today = LocalDate.now();
            LocalDate start;
            LocalDate end;
            if (period == 0) {
                start = today.with(DayOfWeek.MONDAY);
                end = today;
            } else if (period == 1) {
                YearMonth ym = YearMonth.from(today);
                start = ym.atDay(1);
                end = today;
            } else {
                start = firstUsed(today);
                end = today;
            }
            if (start.isAfter(end)) return 0;
            int extras = 0;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                DayData dayData = readDay(d);
                extras += dayData.subtypeExtras[PyramidScheme.SUBTYPE_DRINKS];
            }
            return extras;
        }

        private void drawMotivation(Canvas c, RectF r, boolean companionVisible) {
            boolean cozy = isPastelCozy();
            int fill = cozy
                    ? (dark ? Color.rgb(67, 56, 73) : Color.rgb(255, 249, 238))
                    : alpha(accent, dark ? 35 : 28);
            int stroke = cozy
                    ? (dark ? Color.rgb(155, 131, 166) : Color.rgb(221, 203, 176))
                    : outline;
            int iconCol = cozy && dark ? Color.rgb(238, 215, 247) : accent;

            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            c.drawRoundRect(r, dp(16), dp(16), p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(stroke);
            c.drawRoundRect(r, dp(16), dp(16), p);

            RectF bulbCircle = new RectF(
                    startX(r, dp(14), dp(36)),
                    r.centerY() - dp(18),
                    startX(r, dp(14), dp(36)) + dp(36),
                    r.centerY() + dp(18));
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(iconCol, dark ? 42 : 28));
            c.drawCircle(bulbCircle.centerX(), bulbCircle.centerY(), dp(18), p);
            drawBulb(c, bulbCircle.centerX(), bulbCircle.centerY() + dp(1), iconCol);

            float textStart = startX(r, dp(60));
            if (!companionVisible) {
                RectF heart = new RectF(
                        endX(r, dp(46), dp(28)),
                        r.centerY() - dp(14),
                        endX(r, dp(46), dp(28)) + dp(28),
                        r.centerY() + dp(14));
                drawHeart(c, heart, iconCol);

                float textEnd = isRtl() ? heart.right + dp(10) : heart.left - dp(10);
                float maxTextWidth = Math.max(dp(120), Math.abs(textEnd - textStart));

                text.setTextAlign(startAlign());
                text.setFakeBoldText(true);
                float titleSize = dp(14);
                text.setTextSize(titleSize);
                String motivationTitle = s(R.string.motivation_title);
                while (text.measureText(motivationTitle) > maxTextWidth && titleSize > dp(11)) {
                    titleSize -= dp(1);
                    text.setTextSize(titleSize);
                }
                text.setColor(primaryText);
                c.drawText(motivationTitle, textStart, r.top + dp(28), text);

                text.setFakeBoldText(false);
                float subSize = dp(12);
                text.setTextSize(subSize);
                String motivationBody = s(R.string.motivation_body);
                while (text.measureText(motivationBody) > maxTextWidth && subSize > dp(9)) {
                    subSize -= dp(1);
                    text.setTextSize(subSize);
                }
                text.setColor(secondaryText);
                c.drawText(motivationBody, textStart, r.top + dp(49), text);
                return;
            }

            float textEnd = isRtl() ? r.left + dp(106) : r.right - dp(106);
            float maxTextWidth = Math.max(dp(86), Math.abs(textEnd - textStart));

            text.setTextAlign(startAlign());
            text.setFakeBoldText(true);
            float titleSize = dp(14);
            text.setTextSize(titleSize);
            String motivationTitle = s(R.string.motivation_title);
            while (text.measureText(motivationTitle) > maxTextWidth && titleSize > dp(9.5f)) {
                titleSize -= dp(.5f);
                text.setTextSize(titleSize);
            }
            text.setColor(primaryText);
            c.drawText(
                    ellipsizeText(motivationTitle, maxTextWidth),
                    textStart,
                    r.top + dp(28),
                    text);

            text.setFakeBoldText(false);
            float subSize = dp(12);
            text.setTextSize(subSize);
            String motivationBody = s(R.string.motivation_body);
            while (text.measureText(motivationBody) > maxTextWidth && subSize > dp(8.5f)) {
                subSize -= dp(.5f);
                text.setTextSize(subSize);
            }
            text.setColor(secondaryText);
            c.drawText(
                    ellipsizeText(motivationBody, maxTextWidth),
                    textStart,
                    r.top + dp(49),
                    text);
        }

        private void drawBottomNav(Canvas c, float w, float h, float bottomInset) {
            float navH = dp(54);
            float top = h - bottomInset - navH;
            p.setStyle(Paint.Style.FILL);
            p.setColor(bottomNavBackgroundColor());
            c.drawRoundRect(new RectF(0, top, w, h + dp(16)), dp(18), dp(18), p);

            float itemW = w / 4f;
            int[] tabs = {TAB_OVERVIEW, TAB_TODAY, TAB_PORTIONS, TAB_STATS};
            int[] labels = {R.string.nav_overview, R.string.nav_today, R.string.nav_portions, R.string.nav_statistics};
            for (int i = 0; i < tabs.length; i++) {
                int visualIndex = isRtl() ? tabs.length - 1 - i : i;
                drawNavItem(c, new RectF(itemW * visualIndex, top, itemW * (visualIndex + 1), top + navH), tabs[i], s(labels[i]));
            }
        }

        private int bottomNavBackgroundColor() {
            return card;
        }

        private void drawNavItem(Canvas c, RectF r, int tab, String label) {
            boolean active = activeTab == tab;
            int col = active ? accent : secondaryText;
            if (active) {
                RectF pill = new RectF(r.left + dp(10), r.top + dp(5), r.right - dp(10), r.bottom - dp(5));
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark ? alpha(accent, 34) : alpha(accent, 24));
                c.drawRoundRect(pill, dp(12), dp(12), p);
            }

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(active);
            float labelSize = dp(9.4f);
            text.setTextSize(labelSize);
            while (text.measureText(label) > r.width() - dp(8) && labelSize > dp(8.2f)) {
                labelSize -= dp(.5f);
                text.setTextSize(labelSize);
            }
            text.setColor(col);
            c.drawText(label, r.centerX(), r.bottom - dp(7), text);
            text.setFakeBoldText(false);

            float iconY = r.top + dp(20);
            if (tab == TAB_OVERVIEW) drawCalendarIcon(c, r.centerX(), iconY, col);
            else if (tab == TAB_TODAY) drawSmallPyramidIcon(c, r.centerX(), iconY + dp(1), col);
            else if (tab == TAB_PORTIONS) drawPieChartIcon(c, r.centerX(), iconY, col);
            else drawBarsIcon(c, r.centerX(), iconY, col);

            targets.add(new TouchTarget(r, () -> {
                activeTab = tab;
                if (tab == TAB_TODAY) {
                    changeSelectedDate(LocalDate.now());
                }
            }));
        }

        private void drawPieChartIcon(Canvas c, float cx, float cy, int col) {
            float r = dp(13);
            RectF arc = new RectF(cx - r, cy - r, cx + r, cy + r);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(col, dark ? 60 : 48));
            c.drawCircle(cx, cy, r, p);

            p.setColor(col);
            c.drawArc(arc, -90, 270, true, p);

            p.setColor(bottomNavBackgroundColor());
            c.drawArc(new RectF(cx - r * .72f, cy - r * .72f, cx + r * .72f, cy + r * .72f), -90, 90, true, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.3f));
            p.setColor(col);
            c.drawCircle(cx, cy, r, p);
        }
        private void drawPortionsScreen(Canvas c, float left, float top, float right, float bottom) {
            // The individual category cards provide the visual grouping. Avoid wrapping
            // them in another large card so the table can use the available screen space.
            float contentTop = top + dp(2);
            float contentBottom = bottom - dp(2);
            float insetLeft = left + dp(4);
            float insetRight = right - dp(4);
            RectF viewport = new RectF(insetLeft, contentTop, insetRight, contentBottom);
            portionsScrollArea.set(viewport);

            float baseGap = 2.2f;
            float oneLineCard = portionCardHeightDp(1);
            float twoLineCard = portionCardHeightDp(2);
            float threeLineCard = portionCardHeightDp(3);
            float fiveLineCard = portionCardHeightDp(5);
            float baseContentHeight =
                    oneLineCard + twoLineCard
                            + threeLineCard + threeLineCard + threeLineCard
                            + fiveLineCard + twoLineCard + oneLineCard + 32f
                            + baseGap * 8f;
            float available = Math.max(dp(320), viewport.height());
            float scale = Math.min(1.05f, Math.max(.84f, available / dp(baseContentHeight)));

            // A few pixels of residual overflow should be absorbed by a tiny scale
            // adjustment instead of enabling a nearly motionless scrollbar.
            float predictedContentHeight = dp(baseContentHeight) * scale;
            float tinyOverflow = predictedContentHeight - viewport.height();
            if (tinyOverflow > 0f && tinyOverflow <= dp(8)) {
                scale *= viewport.height() / predictedContentHeight;
            }

            float gap = dp(baseGap) * scale;
            float bodyWidth = insetRight - insetLeft;
            float hDrinks = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_drinks_title),
                    a(R.array.portions_drinks_examples),
                    a(R.array.portions_drinks_measures),
                    false,
                    dp(oneLineCard) * scale,
                    scale);
            float hProduce = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_produce_title),
                    a(R.array.portions_produce_examples),
                    a(R.array.portions_produce_measures),
                    false,
                    dp(twoLineCard) * scale,
                    scale);
            float hThreeLines = dp(threeLineCard) * scale;
            float hGrains = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_grains_title),
                    a(R.array.portions_grains_examples),
                    a(R.array.portions_grains_measures),
                    false,
                    hThreeLines,
                    scale);
            float hDairy = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_dairy_title),
                    a(R.array.portions_dairy_examples),
                    a(R.array.portions_dairy_measures),
                    false,
                    hThreeLines,
                    scale);
            float hNuts = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_nuts_title),
                    a(R.array.portions_nuts_examples),
                    a(R.array.portions_nuts_measures),
                    true,
                    hThreeLines,
                    scale);
            float hProtein = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_protein_title),
                    a(R.array.portions_protein_examples),
                    a(R.array.portions_protein_measures),
                    false,
                    dp(fiveLineCard) * scale,
                    scale);
            float hFats = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_fats_title),
                    a(R.array.portions_fats_examples),
                    a(R.array.portions_fats_measures),
                    true,
                    dp(twoLineCard) * scale,
                    scale);
            float hSingle = measurePortionCategoryHeight(bodyWidth,
                    s(R.string.portions_extras_title),
                    a(R.array.portions_extras_examples),
                    a(R.array.portions_extras_measures),
                    false,
                    dp(oneLineCard) * scale,
                    scale);
            float hHint = dp(32) * scale;

            float totalContentHeight =
                    hDrinks + hProduce + hGrains + hDairy + hNuts + hProtein
                            + hFats + hSingle + hHint
                            + gap * 8f;

            float overflow = Math.max(0f, totalContentHeight - viewport.height());
            if (overflow <= dp(1)) {
                portionsMaxScrollY = 0f;
                portionsScrollY = 0f;
            } else {
                portionsMaxScrollY = overflow;
                portionsScrollY = Math.max(0f, Math.min(portionsScrollY, portionsMaxScrollY));
            }

            int save = c.save();
            c.clipRect(viewport);
            c.translate(0f, -portionsScrollY);

            float y = contentTop;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_GREEN, TYPE_WATER,
                    s(R.string.portions_drinks_title),
                    a(R.array.portions_drinks_examples),
                    a(R.array.portions_drinks_measures),
                    false, hDrinks, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_GREEN, TYPE_PRODUCE,
                    s(R.string.portions_produce_title),
                    a(R.array.portions_produce_examples),
                    a(R.array.portions_produce_measures),
                    false, hProduce, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_GREEN, TYPE_WHEAT_POTATO,
                    s(R.string.portions_grains_title),
                    a(R.array.portions_grains_examples),
                    a(R.array.portions_grains_measures),
                    false, hGrains, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_YELLOW, TYPE_MILK_CHEESE,
                    s(R.string.portions_dairy_title),
                    a(R.array.portions_dairy_examples),
                    a(R.array.portions_dairy_measures),
                    false, hDairy, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_YELLOW, TYPE_NUTS,
                    s(R.string.portions_nuts_title),
                    a(R.array.portions_nuts_examples),
                    a(R.array.portions_nuts_measures),
                    true, hNuts, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_YELLOW, TYPE_PROTEIN,
                    s(R.string.portions_protein_title),
                    a(R.array.portions_protein_examples),
                    a(R.array.portions_protein_measures),
                    false, hProtein, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_YELLOW, TYPE_OIL,
                    s(R.string.portions_fats_title),
                    a(R.array.portions_fats_examples),
                    a(R.array.portions_fats_measures),
                    true, hFats, scale) + gap;

            y = drawPortionCategoryBlock(c, insetLeft, y, insetRight, CAT_RED, TYPE_DESSERT,
                    s(R.string.portions_extras_title),
                    a(R.array.portions_extras_examples),
                    a(R.array.portions_extras_measures),
                    false, hSingle, scale) + gap;

            drawPortionReferenceHint(c, insetLeft, y, insetRight, y + hHint, scale);

            c.restoreToCount(save);

            drawPortionScrollIndicator(c, viewport);
        }

        private float portionCardHeightDp(int lineCount) {
            float header = 17f;
            float body = Math.max(30f, 14f + 11.5f * Math.max(1, lineCount));
            return header + body;
        }

        private float drawPortionCategoryBlock(
                Canvas c,
                float left,
                float top,
                float right,
                int category,
                int iconType,
                String title,
                String[] names,
                String[] measures,
                boolean sharedMeasure,
                float height,
                float scale) {
            PortionCategoryMetrics metrics = buildPortionCategoryMetrics(
                    right - left, title, names, measures, sharedMeasure, height, scale);
            RectF r = new RectF(left, top, right, top + metrics.cardHeight);
            int col = categoryColor(category);
            int border = categoryBorder(category);

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 15) : alpha(Color.WHITE, 164));
            c.drawRoundRect(r, dp(13), dp(13), p);

            drawPortionBodyTint(c, r, col);

            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(alpha(border, dark ? 118 : 145));
            c.drawRoundRect(r, dp(13), dp(13), p);

            float headerH = dp(17) * scale;
            RectF header = new RectF(r.left, r.top, r.right, r.top + headerH);

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(flatCategoryHeaderFill(category));
            c.drawRoundRect(header, dp(13), dp(13), p);
            c.drawRect(header.left, header.centerY(), header.right, header.bottom, p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            float headerTextSize = dp(10.2f) * scale;
            text.setTextSize(headerTextSize);
            while (text.measureText(title) > header.width() - dp(20) && headerTextSize > dp(8.7f) * scale) {
                headerTextSize -= dp(.35f);
                text.setTextSize(headerTextSize);
            }
            text.setColor(categoryReadableText(category));
            c.drawText(title, header.centerX(), header.top + dp(12.2f) * scale, text);
            text.setFakeBoldText(false);

            float bodyTop = header.bottom;
            float bodyBottom = r.bottom;
            float bodyH = Math.max(dp(20), bodyBottom - bodyTop);

            float symbolColLeft = isRtl()
                    ? r.right - dp(8) - dp(48) * scale
                    : r.left + dp(8);
            float symbolColW = dp(48) * scale;
            float nameX = isRtl() ? symbolColLeft - dp(11) : symbolColLeft + symbolColW + dp(11);
            float rightPadding = dp(10);

            float iconSize = dp(29) * scale;
            RectF iconBubble = new RectF(
                    symbolColLeft + (symbolColW - iconSize) / 2f,
                    bodyTop + (bodyH - iconSize) / 2f,
                    symbolColLeft + (symbolColW + iconSize) / 2f,
                    bodyTop + (bodyH + iconSize) / 2f);

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.BLACK, 28) : alpha(Color.WHITE, 118));
            c.drawRoundRect(iconBubble, dp(8.5f), dp(8.5f), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(.8f));
            p.setColor(alpha(border, dark ? 130 : 145));
            c.drawRoundRect(iconBubble, dp(8.5f), dp(8.5f), p);
            drawFoodIcon(c, inset(iconBubble, iconSize * .14f, iconSize * .14f), iconType, false);

            text.setTextAlign(startAlign());
            text.setFakeBoldText(false);
            text.setTextSize(metrics.textSize);
            text.setColor(primaryText);

            float contentTop = bodyTop + Math.max(0f, (bodyH - metrics.contentHeight) / 2f);
            float rowTop = contentTop;
            float nameDrawX = nameX;
            float measureRight = isRtl() ? r.left + rightPadding : r.right - rightPadding;
            for (int i = 0; i < metrics.rowHeights.length; i++) {
                float rowHeight = metrics.rowHeights[i];
                float nameHeight = metrics.nameLineCounts[i] * metrics.lineHeight;
                float nameY = rowTop + (rowHeight - nameHeight) / 2f + metrics.baselineOffset;
                if (i < names.length && names[i].length() > 0) {
                    text.setTextAlign(startAlign());
                    drawWrappedText(c, names[i], nameDrawX, nameY, metrics.nameMaxWidth, metrics.lineHeight);
                }
                if (!sharedMeasure && i < measures.length && measures[i].length() > 0) {
                    float measureHeight = metrics.measureLineCounts[i] * metrics.lineHeight;
                    float measureY = rowTop + (rowHeight - measureHeight) / 2f + metrics.baselineOffset;
                    text.setTextAlign(endAlign());
                    drawWrappedText(c, measures[i], measureRight, measureY, metrics.measureMaxWidth, metrics.lineHeight);
                }
                rowTop += rowHeight + metrics.rowGap;
            }

            if (sharedMeasure && measures.length > 0 && measures[0].length() > 0) {
                float measureHeight = metrics.sharedMeasureLineCount * metrics.lineHeight;
                float measureY = bodyTop + (bodyH - measureHeight) / 2f + metrics.baselineOffset;
                text.setTextAlign(endAlign());
                drawWrappedText(c, measures[0], measureRight, measureY, metrics.measureMaxWidth, metrics.lineHeight);
            }

            return r.bottom;
        }

        private float measurePortionCategoryHeight(
                float width,
                String title,
                String[] names,
                String[] measures,
                boolean sharedMeasure,
                float minHeight,
                float scale) {
            return buildPortionCategoryMetrics(width, title, names, measures, sharedMeasure, minHeight, scale)
                    .cardHeight;
        }

        private PortionCategoryMetrics buildPortionCategoryMetrics(
                float width,
                String title,
                String[] names,
                String[] measures,
                boolean sharedMeasure,
                float minHeight,
                float scale) {
            float headerH = dp(17) * scale;
            float bodyMinH = Math.max(dp(20), minHeight - headerH);
            float bodyWidth = width;
            float symbolColLeft = dp(8);
            float symbolColW = dp(48) * scale;
            float nameX = symbolColLeft + symbolColW + dp(11);
            float rightPadding = dp(10);
            float columnGap = dp(10) * scale;
            float measureColumnWidth = Math.max(dp(110) * scale, (bodyWidth - nameX - rightPadding) * .34f);
            measureColumnWidth = Math.min(measureColumnWidth, Math.max(dp(98) * scale, bodyWidth - nameX - rightPadding - dp(40) * scale));
            float measureX = bodyWidth - rightPadding - measureColumnWidth;
            float nameMaxW = Math.max(dp(72) * scale, measureX - nameX - columnGap);
            float measureMaxW = Math.max(dp(90) * scale, measureColumnWidth);

            float textSize = dp(9.05f) * scale;
            float lineHeight = dp(10.8f) * scale;
            float rowGap = dp(2.2f) * scale;
            float baselineOffset = dp(3.9f) * scale;

            text.setTextSize(textSize);
            int rowCount = Math.max(names.length, sharedMeasure ? names.length : measures.length);
            if (rowCount == 0) rowCount = 1;
            float[] rowHeights = new float[rowCount];
            int[] nameLineCounts = new int[rowCount];
            int[] measureLineCounts = new int[rowCount];
            float contentHeight = 0f;
            for (int i = 0; i < rowCount; i++) {
                int nameLines = wrappedLineCount(i < names.length ? names[i] : "", nameMaxW);
                nameLineCounts[i] = nameLines;
                int measureLines = sharedMeasure ? 0 : wrappedLineCount(i < measures.length ? measures[i] : "", measureMaxW);
                measureLineCounts[i] = measureLines;
                float rowHeight = Math.max(Math.max(1, nameLines), Math.max(1, measureLines)) * lineHeight;
                rowHeights[i] = rowHeight;
                contentHeight += rowHeight;
                if (i < rowCount - 1) contentHeight += rowGap;
            }
            int sharedMeasureLineCount = sharedMeasure
                    ? wrappedLineCount(measures.length > 0 ? measures[0] : "", measureMaxW)
                    : 0;
            float requiredContentHeight = Math.max(contentHeight, sharedMeasureLineCount * lineHeight);
            float bodyHeight = Math.max(bodyMinH, requiredContentHeight + dp(10) * scale);
            return new PortionCategoryMetrics(
                    headerH + bodyHeight,
                    textSize,
                    lineHeight,
                    baselineOffset,
                    rowGap,
                    nameMaxW,
                    measureMaxW,
                    rowHeights,
                    nameLineCounts,
                    measureLineCounts,
                    requiredContentHeight,
                    sharedMeasureLineCount);
        }

        private void drawFittedText(Canvas c, String value, float x, float y, float maxW, float startSize, float minSize, boolean bold) {
            text.setFakeBoldText(bold);
            float size = startSize;
            text.setTextSize(size);
            while (text.measureText(value) > maxW && size > minSize) {
                size -= dp(.3f);
                text.setTextSize(size);
            }
            c.drawText(value, x, y, text);
            text.setFakeBoldText(false);
        }

        private int flatCategoryHeaderFill(int category) {
            if (category == CAT_RED) return dark ? Color.rgb(70, 44, 53) : Color.rgb(255, 232, 235);
            if (category == CAT_YELLOW) return dark ? Color.rgb(68, 58, 42) : Color.rgb(255, 246, 221);
            return dark ? Color.rgb(52, 64, 50) : Color.rgb(238, 247, 231);
        }

        private int categoryReadableText(int category) {
            if (category == CAT_RED) return dark ? Color.rgb(255, 207, 214) : Color.rgb(151, 67, 76);
            if (category == CAT_YELLOW) return dark ? Color.rgb(245, 217, 160) : Color.rgb(126, 89, 31);
            return dark ? Color.rgb(199, 228, 184) : Color.rgb(61, 111, 69);
        }

        private void drawPortionBodyTint(Canvas c, RectF r, int col) {
            p.setShader(new LinearGradient(isRtl() ? r.right : r.left, r.top, isRtl() ? r.left : r.right, r.top,
                    new int[] {
                            alpha(col, dark ? 12 : 18),
                            alpha(col, dark ? 6 : 9),
                            alpha(col, 0)
                    },
                    new float[] { 0f, .44f, 1f },
                    Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(r, dp(13), dp(13), p);
            p.setShader(null);
        }

        private void drawPortionScrollIndicator(Canvas c, RectF viewport) {
            if (portionsMaxScrollY <= dp(1)) return;

            float trackW = dp(3);
            RectF track = isRtl()
                    ? new RectF(viewport.left, viewport.top + dp(4), viewport.left + trackW, viewport.bottom - dp(4))
                    : new RectF(viewport.right - trackW, viewport.top + dp(4), viewport.right, viewport.bottom - dp(4));

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 28) : alpha(Color.BLACK, 18));
            c.drawRoundRect(track, dp(2), dp(2), p);

            float visible = viewport.height();
            float total = visible + portionsMaxScrollY;
            float thumbH = Math.max(dp(28), track.height() * visible / total);
            float travel = Math.max(0f, track.height() - thumbH);
            float top = track.top + travel * (portionsScrollY / portionsMaxScrollY);

            RectF thumb = new RectF(track.left, top, track.right, top + thumbH);
            p.setColor(alpha(accent, dark ? 150 : 130));
            c.drawRoundRect(thumb, dp(2), dp(2), p);
        }

        private void drawOverlayScrollIndicator(Canvas c, RectF body, float scrollY, float maxScrollY) {
            if (maxScrollY <= dp(1) || body.height() <= dp(40)) return;

            float trackW = dp(3);
            RectF track = isRtl()
                    ? new RectF(body.left, body.top + dp(4), body.left + trackW, body.bottom - dp(4))
                    : new RectF(body.right - trackW, body.top + dp(4), body.right, body.bottom - dp(4));

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 30) : alpha(Color.BLACK, 18));
            c.drawRoundRect(track, dp(2), dp(2), p);

            float visible = body.height();
            float total = visible + maxScrollY;
            float thumbH = Math.max(dp(28), track.height() * visible / total);
            float travel = Math.max(0f, track.height() - thumbH);
            float top = track.top + travel * (scrollY / maxScrollY);

            RectF thumb = new RectF(track.left, top, track.right, top + thumbH);
            p.setColor(alpha(accent, dark ? 150 : 130));
            c.drawRoundRect(thumb, dp(2), dp(2), p);
        }

        private float measureWrappedTextHeight(String entry, float maxWidth, float lineHeight) {
            return Math.max(1, wrapTextLines(entry, maxWidth).size()) * lineHeight;
        }

        private float measureWrappedBulletHeight(String entry, float maxWidth, float lineHeight) {
            return measureWrappedTextHeight(entry, maxWidth, lineHeight);
        }

        private void drawPortionReferenceHint(Canvas c, float left, float top, float right, float bottom, float scale) {
            if (bottom <= top + dp(20)) return;
            RectF hint = new RectF(left, top, right, bottom);

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 10) : alpha(Color.WHITE, 118));
            c.drawRoundRect(hint, dp(13), dp(13), p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(alpha(outline, dark ? 150 : 180));
            c.drawRoundRect(hint, dp(13), dp(13), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(13) * scale);
            text.setColor(accent);
            c.drawText(s(R.string.symbol_chevron), endX(hint, dp(13)), hint.centerY() + dp(4) * scale, text);

            text.setTextAlign(startAlign());
            text.setFakeBoldText(false);
            text.setTextSize(dp(9.4f) * scale);
            text.setColor(primaryText);
            drawWrappedText(c,
                    s(R.string.portions_reference_hint),
                    startX(hint, dp(12)),
                    hint.top + dp(16) * scale,
                    hint.width() - dp(38),
                    dp(12.4f) * scale);

            RectF touchHint = new RectF(hint);
            touchHint.offset(0f, -portionsScrollY);
            if (RectF.intersects(touchHint, portionsScrollArea)) {
                touchHint.intersect(portionsScrollArea);
                targets.add(new TouchTarget(touchHint, this::openBzfeSourceLink));
            }
        }

        private void drawSettings(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 110 : 70));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, () -> { }));

            RectF panel = new RectF(dp(24), dp(52), w - dp(24), h - dp(52));
            drawCard(c, panel);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(20));
            text.setColor(accent);
            c.drawText(s(R.string.settings_title), panel.centerX(), panel.top + dp(34), text);
            text.setFakeBoldText(false);

            RectF close = endRect(panel.left, panel.right, dp(10), dp(36), panel.top + dp(10), dp(36));
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText(s(R.string.symbol_close), close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, this::closeSettingsOverlay));

            RectF body = new RectF(panel.left + dp(20), panel.top + dp(60), panel.right - dp(20), panel.bottom - dp(20));
            settingsScrollArea.set(body);
            float contentH = measureSettingsBodyHeight(body.width());
            if (Math.abs(body.width() - settingsLastBodyW) > .5f
                    || Math.abs(body.height() - settingsLastBodyH) > .5f
                    || Math.abs(contentH - settingsLastContentH) > .5f) {
                settingsScrollY = 0f;
                settingsLastBodyW = body.width();
                settingsLastBodyH = body.height();
                settingsLastContentH = contentH;
            }
            settingsMaxScrollY = Math.max(0f, contentH - body.height());
            settingsScrollY = clampOverlayScroll(settingsScrollY, settingsMaxScrollY);

            int save = c.save();
            c.clipRect(body);
            layoutSettingsBody(c, panel, body, settingsScrollY, true);

            c.restoreToCount(save);
            drawOverlayScrollIndicator(c, body, settingsScrollY, settingsMaxScrollY);
        }

        private float measureSettingsBodyHeight(float width) {
            RectF panel = new RectF(0f, 0f, width + dp(40), 0f);
            RectF body = new RectF(dp(20), 0f, panel.right - dp(20), 0f);
            return layoutSettingsBody(null, panel, body, 0f, false);
        }

        private float layoutSettingsBody(Canvas c, RectF panel, RectF body, float scrollY, boolean draw) {
            float left = panel.left + dp(20);
            float right = panel.right - dp(20);
            float y = body.top - scrollY + dp(SETTINGS_SECTION_TOP_GAP_DP);

            y = drawSettingsSectionLabel(c, startX(panel, dp(22)), y, s(R.string.settings_language_description), draw);
            RectF language = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                settingsLanguageSelectorRect.set(language);
                drawSettingsSelector(c,
                        language,
                        s(R.string.settings_language),
                        currentLanguageLabel(),
                        SETTINGS_MENU_LANGUAGE);
                addVisibleTouchTarget(body, language, () -> toggleSettingsMenu(SETTINGS_MENU_LANGUAGE));
            }
            y = language.bottom;
            if (settingsOpenMenu == SETTINGS_MENU_LANGUAGE) {
                y = layoutSettingsSelectorMenu(c, body, left, right, y, SETTINGS_MENU_LANGUAGE, draw);
            }
            y += dp(SETTINGS_CARD_GAP_DP);

            RectF theme = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                settingsThemeSelectorRect.set(theme);
                drawSettingsSelector(c,
                        theme,
                        s(R.string.settings_color_scheme),
                        currentThemeLabel(),
                        SETTINGS_MENU_THEME);
                addVisibleTouchTarget(body, theme, () -> toggleSettingsMenu(SETTINGS_MENU_THEME));
            }
            y = theme.bottom;
            if (settingsOpenMenu == SETTINGS_MENU_THEME) {
                y = layoutSettingsSelectorMenu(c, body, left, right, y, SETTINGS_MENU_THEME, draw);
            }
            y += dp(SETTINGS_SECTION_GAP_DP);

            y = drawSettingsSectionLabel(
                    c,
                    startX(panel, dp(22)),
                    y,
                    s(R.string.settings_companion_section),
                    draw);
            RectF companion = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                CompanionUiState.ViewState companionState = companionUiState;
                String companionDescription = !companionState.available
                        ? s(R.string.companion_unavailable)
                        : companionState.presentation.enabled
                                ? s(R.string.companion_enabled)
                                : s(R.string.companion_disabled);
                drawSettingsToggleCard(
                        c,
                        companion,
                        s(R.string.settings_companion),
                        companionDescription,
                        companionState.presentation.enabled,
                        companionState.available);
                if (companionState.available) {
                    addVisibleTouchTarget(body, companion, this::toggleCompanionEnabled);
                }
            }
            y = companion.bottom + dp(SETTINGS_SECTION_GAP_DP);

            y = drawSettingsSectionLabel(c, startX(panel, dp(22)), y, s(R.string.settings_lookup), draw);
            RectF portions = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                drawSettingsLinkCard(c,
                        portions,
                        s(R.string.settings_portion_help),
                        s(R.string.settings_portion_help_description));
                addVisibleTouchTarget(body, portions, () -> {
                    activeTab = TAB_PORTIONS;
                    closeSettingsOverlay();
                    aboutOpen = false;
                    changelogOpen = false;
                    licenseOpen = false;
                    portionHelpOpen = false;
                });
            }
            y = portions.bottom + dp(SETTINGS_CARD_GAP_DP);

            RectF bzfe = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                drawSettingsLinkCard(c,
                        bzfe,
                        s(R.string.settings_bzfe_source),
                        s(R.string.settings_bzfe_source_description));
                addVisibleTouchTarget(body, bzfe, this::openBzfeSourceLink);
            }
            y = bzfe.bottom + dp(SETTINGS_SECTION_GAP_DP);

            y = drawSettingsSectionLabel(c, startX(panel, dp(22)), y, s(R.string.settings_style), draw);
            RectF backup = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                drawSettingsLinkCard(c,
                        backup,
                        s(R.string.settings_backup),
                        s(R.string.settings_backup_description));
                addVisibleTouchTarget(body, backup, this::showBackupOptions);
            }
            y = backup.bottom + dp(SETTINGS_SECTION_GAP_DP);

            y = drawSettingsSectionLabel(c, startX(panel, dp(22)), y, s(R.string.settings_about), draw);
            RectF about = new RectF(left, y, right, y + dp(SETTINGS_ROW_HEIGHT_DP));
            if (draw) {
                drawSettingsLinkCard(c,
                        about,
                        s(R.string.app_name),
                        s(R.string.settings_about_description));
                addVisibleTouchTarget(body, about, this::openAboutOverlayFromSettings);
            }
            return about.bottom - body.top + dp(SETTINGS_BOTTOM_GAP_DP);
        }

        private float drawSettingsSectionLabel(Canvas c, float x, float y, String label, boolean draw) {
            if (draw) {
                text.setFakeBoldText(false);
                text.setTextAlign(startAlign());
                text.setTextSize(dp(13));
                text.setColor(primaryText);
                c.drawText(label, x, y, text);
            }
            return y + dp(SETTINGS_SECTION_TO_CARD_GAP_DP);
        }

        private void drawSettingsLinkCard(Canvas c, RectF cardBounds, String title, String description) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 20) : alpha(Color.WHITE, 135));
            c.drawRoundRect(cardBounds, dp(13), dp(13), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(cardBounds, dp(13), dp(13), p);

            text.setTextAlign(startAlign());
            text.setTextSize(dp(13));
            text.setColor(primaryText);
            c.drawText(title, startX(cardBounds, dp(14)), cardBounds.top + dp(19), text);
            text.setTextSize(dp(10.5f));
            text.setColor(secondaryText);
            drawFittedText(c, description, startX(cardBounds, dp(14)), cardBounds.top + dp(36),
                    cardBounds.width() - dp(50), dp(10.5f), dp(8.5f), false);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(20));
            text.setColor(accent);
            c.drawText(isRtl() ? "\u2039" : "\u203a", endX(cardBounds, dp(22)), cardBounds.centerY() + dp(7), text);
        }

        private void drawSettingsToggleCard(
                Canvas c,
                RectF cardBounds,
                String title,
                String description,
                boolean enabled,
                boolean available) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 20) : alpha(Color.WHITE, 135));
            c.drawRoundRect(cardBounds, dp(13), dp(13), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(available ? outline : alpha(outline, 120));
            c.drawRoundRect(cardBounds, dp(13), dp(13), p);

            float switchWidth = dp(44);
            float switchHeight = dp(24);
            RectF toggle = new RectF(
                    endX(cardBounds, dp(14), switchWidth),
                    cardBounds.centerY() - switchHeight / 2f,
                    endX(cardBounds, dp(14), switchWidth) + switchWidth,
                    cardBounds.centerY() + switchHeight / 2f);

            p.setStyle(Paint.Style.FILL);
            p.setColor(!available
                    ? alpha(secondaryText, 48)
                    : enabled
                            ? alpha(accent, dark ? 190 : 210)
                            : alpha(secondaryText, dark ? 80 : 62));
            c.drawRoundRect(toggle, toggle.height() / 2f, toggle.height() / 2f, p);

            float knobRadius = dp(9);
            float knobX = enabled == isRtl() ? toggle.left + dp(12) : toggle.right - dp(12);
            p.setColor(available
                    ? (enabled ? Color.WHITE : (dark ? Color.rgb(214, 207, 200) : Color.WHITE))
                    : alpha(Color.WHITE, 145));
            c.drawCircle(knobX, toggle.centerY(), knobRadius, p);

            float textWidth = Math.abs((isRtl() ? toggle.right : toggle.left) - startX(cardBounds, dp(14))) - dp(10);
            text.setTextAlign(startAlign());
            text.setTextSize(dp(13));
            text.setColor(available ? primaryText : alpha(primaryText, 145));
            c.drawText(title, startX(cardBounds, dp(14)), cardBounds.top + dp(19), text);
            text.setTextSize(dp(10.5f));
            text.setColor(available ? secondaryText : alpha(secondaryText, 145));
            drawFittedText(
                    c,
                    description,
                    startX(cardBounds, dp(14)),
                    cardBounds.top + dp(36),
                    textWidth,
                    dp(10.5f),
                    dp(8.3f),
                    false);
        }

        private CompanionUiState.ViewState loadCompanionUiState() {
            try {
                return CompanionUiState.fromLoad(
                        companionRepository.load(),
                        Instant.now());
            } catch (RuntimeException ignored) {
                return CompanionUiState.fromLoad(null, Instant.now());
            }
        }

        private void toggleCompanionEnabled() {
            CompanionUiState.ViewState current = companionUiState;
            if (!current.available) {
                showSnackbar(s(R.string.snackbar_companion_unavailable));
                return;
            }

            boolean requestedEnabled = !current.presentation.enabled;
            CompanionRepository.MutationResult result;
            try {
                result = companionRepository.setEnabled(requestedEnabled);
            } catch (RuntimeException ignored) {
                result = null;
            }

            CompanionUiState.ToggleOutcome outcome =
                    CompanionUiState.classifyToggle(result, requestedEnabled);
            companionUiState = loadCompanionUiState();
            if (outcome == CompanionUiState.ToggleOutcome.ENABLED) {
                showSnackbar(companionEnabledMessage(companionUiState.presentation));
            } else if (outcome == CompanionUiState.ToggleOutcome.DISABLED) {
                companionPageState.close();
                clearCompanionPageReaction();
                releaseCompanionPageAssets();
                showSnackbar(companionDisabledMessage(companionUiState.presentation));
            } else {
                companionPageState.close();
                clearCompanionPageReaction();
                releaseCompanionPageAssets();
                showSnackbar(s(R.string.snackbar_companion_unavailable));
            }
            invalidate();
        }

        private void drawCompanionPreview(
                Canvas c,
                RectF bounds,
                CompanionPresentation.Snapshot snapshot) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 22) : alpha(Color.WHITE, 175));
            c.drawRoundRect(bounds, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(bounds, dp(14), dp(14), p);

            float artSize = Math.min(dp(32), Math.min(bounds.height() - dp(10), bounds.width() * .34f));
            float artLeft = startX(bounds, dp(6), artSize);
            float artTop = bounds.centerY() - artSize / 2f;
            RectF art = new RectF(artLeft, artTop, artLeft + artSize, artTop + artSize);
            Rect previewSource = companionSourceBoundsForPose(CompanionPageState.Pose.SLEEPING);
            if (companionArtworkSleepingBmp != null && !previewSource.isEmpty()) {
                float sourceAspect = previewSource.width() / (float) previewSource.height();
                float drawWidth = art.width();
                float drawHeight = drawWidth / sourceAspect;
                if (drawHeight > art.height()) {
                    drawHeight = art.height();
                    drawWidth = drawHeight * sourceAspect;
                }
                companionLambDrawBounds.set(
                        art.centerX() - drawWidth / 2f,
                        art.centerY() - drawHeight / 2f,
                        art.centerX() + drawWidth / 2f,
                        art.centerY() + drawHeight / 2f);
                bitmapSrc.set(previewSource);
                c.drawBitmap(
                        companionArtworkSleepingBmp,
                        bitmapSrc,
                        companionLambDrawBounds,
                        bitmapPaint);
            }

            float textStart = isRtl() ? art.left - dp(5) : art.right + dp(5);
            float textEnd = isRtl() ? bounds.left + dp(6) : bounds.right - dp(6);
            float textWidth = Math.max(dp(1), Math.abs(textEnd - textStart));
            float textCenterX = (textStart + textEnd) / 2f;
            drawCenteredFittedText(
                    c,
                    companionTitleText(snapshot),
                    textCenterX,
                    bounds.top + dp(18),
                    textWidth,
                    dp(9.5f),
                    dp(7.5f),
                    true);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(false);
            text.setTextSize(dp(12));
            text.setColor(accent);
            float tokenSize = dp(11);
            float balanceTextWidth = text.measureText(
                    s(R.string.companion_balance_format, snapshot.balance));
            float balanceGroupWidth = tokenSize + dp(3) + balanceTextWidth;
            float tokenLeft = textCenterX - balanceGroupWidth / 2f;
            drawCompanionTokenSymbol(c, new RectF(
                    tokenLeft,
                    bounds.bottom - dp(19),
                    tokenLeft + tokenSize,
                    bounds.bottom - dp(8)),
                    false);
            c.drawText(
                    s(R.string.companion_balance_format, snapshot.balance),
                    tokenLeft + tokenSize + dp(3) + balanceTextWidth / 2f,
                    bounds.bottom - dp(10),
                    text);
        }

        private void setCoverRectForBitmap(Bitmap bitmap, RectF bounds, RectF outBounds) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                outBounds.set(bounds);
                return;
            }
            float bitmapRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            float boundsRatio = bounds.width() / Math.max(1f, bounds.height());

            if (bitmapRatio > boundsRatio) {
                float scaledWidth = bounds.height() * bitmapRatio;
                float left = bounds.centerX() - scaledWidth / 2f;
                outBounds.set(left, bounds.top, left + scaledWidth, bounds.bottom);
                return;
            }

            float scaledHeight = bounds.width() / bitmapRatio;
            float top = bounds.centerY() - scaledHeight / 2f;
            outBounds.set(bounds.left, top, bounds.right, top + scaledHeight);
        }

        private void openCompanionPage() {
            CompanionUiState.ViewState current = loadCompanionUiState();
            companionUiState = current;
            if (!current.available || !current.presentation.enabled) return;
            cancelCompanionPageRedraw();
            companionPageState.open();
            ensureCompanionPageAssets();
            invalidate();
        }

        private void closeCompanionPage() {
            CompanionUiState.ViewState current = loadCompanionUiState();
            companionUiState = current;
            clearCompanionPageReaction();
            companionPageState.close();
            releaseCompanionPageAssets();
            invalidate();
        }

        boolean handleBackPressed() {
            if (companionPageState.isOpen()) {
                closeCompanionPage();
                return true;
            }
            if (settingsOpen) {
                closeSettingsOverlay();
                return true;
            }
            return false;
        }

        void onHostResumed() {
            if (companionPageState.isOpen()) ensureCompanionPageAssets();
            // Redraw also re-evaluates the local clock for an open companion page.
            invalidate();
        }

        void onHostPaused() {
            cancelCompanionPageRedraw();
            releaseCompanionPageAssets();
        }

        private void drawCompanionPage(Canvas c, float w, float h) {
            targets.clear();
            CompanionUiState.ViewState companion = companionUiState;
            if (!companion.available || !companion.presentation.enabled) {
                closeCompanionPage();
                return;
            }
            targets.add(new TouchTarget(new RectF(0f, 0f, w, h), () -> { }));
            ensureCompanionPageAssets();
            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            c.drawRect(0f, 0f, w, h, p);
            float topInset = systemTopInset();
            float headerTop = topInset + dp(12);
            float headerHeight = dp(44);
            RectF close = startRect(0f, w, dp(12), dp(44), headerTop, headerHeight);
            drawCompanionPageClose(c, close);
            targets.add(new TouchTarget(close, this::closeCompanionPage));

            RectF balance = endRect(0f, w, dp(14), dp(86), headerTop, headerHeight);
            drawCompanionPageBalance(c, balance, companion.presentation.balance);
            RectF title = isRtl()
                    ? new RectF(balance.right + dp(4), headerTop, close.left - dp(4), headerTop + headerHeight)
                    : new RectF(close.right + dp(4), headerTop, balance.left - dp(4), headerTop + headerHeight);
            drawCompanionPageTitle(c, title, companion.presentation);
            targets.add(new TouchTarget(title, this::openCompanionNameEditor));
            float controlsTop = headerTop + headerHeight + dp(10);
            float controlsHeight = dp(62);
            long now = android.os.SystemClock.uptimeMillis();
            boolean interactionsLocked = companionPageState.interactionsLocked(now);
            drawCompanionPageControls(c, new RectF(dp(12), controlsTop, w - dp(12), controlsTop + controlsHeight),
                    companion.presentation.balance, interactionsLocked);
            float sceneBottom = h - systemBottomInset() - dp(14);
            RectF scene = new RectF(dp(12), controlsTop + dp(72), w - dp(12),
                    Math.max(controlsTop + controlsHeight + dp(18), sceneBottom));
            CompanionPageState.Pose pose = companionPageState.displayedPose(now);
            drawCompanionIsometricScene(c, scene, pose, now);
            scheduleCompanionPageRedraw(now);
        }

        private void drawCompanionPageClose(Canvas c, RectF bounds) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 22) : alpha(Color.BLACK, 12));
            c.drawRoundRect(bounds, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(primaryText);
            float point = isRtl() ? bounds.right - dp(16) : bounds.left + dp(16);
            float tail = isRtl() ? bounds.left + dp(13) : bounds.right - dp(13);
            float wing = isRtl() ? bounds.right - dp(25) : bounds.left + dp(25);
            c.drawLine(point, bounds.centerY(), tail, bounds.centerY(), p);
            c.drawLine(point, bounds.centerY(), wing, bounds.top + dp(13), p);
            c.drawLine(point, bounds.centerY(), wing, bounds.bottom - dp(13), p);
        }

        private void drawCompanionPageBalance(Canvas c, RectF bounds, long balance) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 22) : alpha(accent, 22));
            c.drawRoundRect(bounds, dp(14), dp(14), p);
            drawCompanionTokenSymbol(c, new RectF(
                    startX(bounds, dp(8), dp(21)),
                    bounds.top + dp(10),
                    startX(bounds, dp(8), dp(21)) + dp(21),
                    bounds.bottom - dp(10)),
                    false);
            text.setTextAlign(startAlign());
            text.setTextSize(dp(15));
            text.setFakeBoldText(true);
            text.setColor(primaryText);
            drawFittedText(c, String.valueOf(balance), startX(bounds, dp(34)), bounds.centerY() + dp(5),
                    bounds.width() - dp(39), dp(15), dp(9), true);
            text.setFakeBoldText(false);
        }

        private void drawCompanionPageTitle(
                Canvas c,
                RectF bounds,
                CompanionPresentation.Snapshot snapshot) {
            if (bounds.width() <= dp(24)) return;
            float badgeSize = Math.min(dp(24), bounds.height() - dp(12));
            RectF badge = new RectF(
                    startX(bounds, 0f, badgeSize),
                    bounds.centerY() - badgeSize / 2f,
                    startX(bounds, 0f, badgeSize) + badgeSize,
                    bounds.centerY() + badgeSize / 2f);
            drawEditBadge(c, badge);
            RectF titleArea = isRtl()
                    ? new RectF(bounds.left, bounds.top, badge.left - dp(4), bounds.bottom)
                    : new RectF(badge.right + dp(4), bounds.top, bounds.right, bounds.bottom);
            text.setColor(primaryText);
            drawCenteredFittedTextInArea(
                    c,
                    companionTitleText(snapshot),
                    titleArea,
                    dp(16),
                    dp(9),
                    true);
        }

        private void drawCompanionPageControls(
                Canvas c,
                RectF bounds,
                long balance,
                boolean interactionsLocked) {
            List<CompanionInteractionCatalog.Entry> interactions = CompanionInteractionCatalog.all();
            float gap = dp(5);
            float width = (bounds.width() - gap * 2f) / interactions.size();
            for (int i = 0; i < interactions.size(); i++) {
                CompanionInteractionCatalog.Entry interaction = interactions.get(i);
                float x = isRtl()
                        ? bounds.right - width - i * (width + gap)
                        : bounds.left + i * (width + gap);
                RectF control = new RectF(x, bounds.top, x + width, bounds.bottom);
                drawCompanionPageControl(c, control, interaction, balance, interactionsLocked);
                if (!interactionsLocked) {
                    targets.add(new TouchTarget(
                            control,
                            () -> performCompanionPageInteraction(interaction)));
                }
            }
        }

        private void drawCompanionPageControl(Canvas c, RectF bounds,
                CompanionInteractionCatalog.Entry interaction,
                long balance,
                boolean interactionsLocked) {
            boolean affordable = !interactionsLocked
                    && CompanionAffordability.isAffordable(balance, interaction.cost);
            p.setStyle(Paint.Style.FILL);
            p.setColor(affordable
                    ? (dark ? alpha(Color.WHITE, 20) : alpha(Color.WHITE, 185))
                    : (dark ? alpha(Color.WHITE, 10) : alpha(Color.BLACK, 10)));
            c.drawRoundRect(bounds, dp(12), dp(12), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(affordable ? outline : alpha(outline, dark ? 90 : 105));
            c.drawRoundRect(bounds, dp(12), dp(12), p);
            RectF icon = new RectF(startX(bounds, dp(7), dp(20)), bounds.top + dp(8), startX(bounds, dp(7), dp(20)) + dp(20), bounds.top + dp(28));
            drawCompanionControlSymbol(c, icon, interaction.id, !affordable);
            text.setTextAlign(startAlign());
            text.setFakeBoldText(true);
            text.setColor(affordable ? primaryText : iconColor(primaryText, true));
            drawFittedText(c, s(interaction.labelResId), isRtl() ? icon.left - dp(4) : icon.right + dp(4), bounds.top + dp(23),
                    bounds.width() - dp(36), dp(12), dp(8), true);
            text.setFakeBoldText(false);
            text.setColor(affordable ? secondaryText : iconColor(secondaryText, true));
            float costIconSize = dp(13);
            RectF costIcon = new RectF(
                    startX(bounds, dp(9), costIconSize),
                    bounds.bottom - dp(22),
                    startX(bounds, dp(9), costIconSize) + costIconSize,
                    bounds.bottom - dp(9));
            drawCompanionTokenSymbol(c, costIcon, !affordable);
            drawFittedText(c, String.valueOf(interaction.cost), isRtl() ? costIcon.left - dp(3) : costIcon.right + dp(3),
                    bounds.bottom - dp(10), bounds.width() - dp(36),
                    dp(14), dp(8), true);
        }

        private void drawCompanionControlSymbol(
                Canvas c, RectF bounds, String interactionId, boolean muted) {
            Bitmap artwork = "feed_treat".equals(interactionId)
                    ? companionTreatBowlBmp
                    : ("play".equals(interactionId)
                            ? companionPlayBallBmp : companionCuddleHeartBmp);
            if (artwork == null) return;
            int oldAlpha = bitmapPaint.getAlpha();
            bitmapPaint.setAlpha(muted ? 90 : 255);
            c.drawBitmap(artwork, null, bounds, bitmapPaint);
            bitmapPaint.setAlpha(oldAlpha);
        }

        private void drawCompanionTokenSymbol(Canvas c, RectF bounds, boolean muted) {
            if (companionTokenFlowerBmp == null) return;
            int oldAlpha = bitmapPaint.getAlpha();
            bitmapPaint.setAlpha(muted ? 90 : 255);
            c.drawBitmap(companionTokenFlowerBmp, null, bounds, bitmapPaint);
            bitmapPaint.setAlpha(oldAlpha);
        }

        private boolean completeCompanionReaction(String interactionId) {
            long now = android.os.SystemClock.uptimeMillis();
            cancelCompanionPageRedraw();
            if (companionPageState.completeInteraction(interactionId)) {
                scheduleCompanionPageRedraw(now);
                return true;
            }
            return false;
        }

        private void clearCompanionPageReaction() {
            companionPageState.finishReaction();
            cancelCompanionPageRedraw();
        }

        private void scheduleCompanionPageRedraw(long nowMs) {
            cancelCompanionPageRedraw();
            if (!companionPageState.isOpen()) return;
            long delayMs = companionPageState.nextTransitionRemainingMillis(nowMs);
            if (delayMs <= 0L) return;
            if (companionPageState.showsPlayBall(nowMs)) delayMs = Math.min(16L, delayMs);
            postDelayed(companionPageRedrawRunnable, delayMs);
        }

        private void cancelCompanionPageRedraw() {
            removeCallbacks(companionPageRedrawRunnable);
        }

        private void drawCompanionIsometricScene(
                Canvas c,
                RectF bounds,
                CompanionPageState.Pose pose,
                long nowMs) {
            int saved = c.save();
            companionSceneClipPath.reset();
            companionSceneClipPath.addRoundRect(
                    bounds,
                    dp(20),
                    dp(20),
                    android.graphics.Path.Direction.CW);
            c.clipPath(companionSceneClipPath);

            CompanionVisualMode.Mode mode = companionPageVisualMode();
            Bitmap backdrop = mode == CompanionVisualMode.Mode.DAY
                    ? companionBackdropDayBmp : companionBackdropNightBmp;
            Bitmap barnLayer = mode == CompanionVisualMode.Mode.DAY
                    ? companionBarnLayerDayBmp : companionBarnLayerNightBmp;
            Bitmap sceneReference = backdrop != null ? backdrop : barnLayer;
            RectF imageBounds = companionSceneImageBounds;
            setCoverRectForBitmap(sceneReference, bounds, imageBounds);
            if (backdrop != null) {
                c.drawBitmap(backdrop, null, imageBounds, bitmapPaint);
            }
            if (barnLayer != null) {
                c.drawBitmap(barnLayer, null, imageBounds, bitmapPaint);
            }

            RectF lambBounds = companionLambDrawBounds;
            companionLambBounds(bounds, imageBounds, pose, lambBounds);
            if (companionPageState.showsTreatBowl(nowMs)) {
                drawCompanionTreatBowl(c, bounds, lambBounds, pose);
            } else if (companionPageState.showsPlayBall(nowMs)) {
                drawCompanionPlayBall(
                        c,
                        bounds,
                        lambBounds,
                        companionPageState.reactionProgress(nowMs));
            }
            drawCompanionIsometricLamb(c, lambBounds, pose);
            if (companionPageState.showsCuddleHearts(nowMs)) {
                drawCompanionCuddleHearts(c, lambBounds);
            }
            c.restoreToCount(saved);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(dark ? alpha(Color.WHITE, 46) : alpha(Color.rgb(100, 128, 96), 54));
            c.drawRoundRect(bounds, dp(20), dp(20), p);
        }

        private CompanionVisualMode.Mode companionPageVisualMode() {
            return CompanionVisualMode.forLocalHour(LocalTime.now().getHour());
        }

        private void companionLambBounds(
                RectF scene,
                RectF imageBounds,
                CompanionPageState.Pose pose,
                RectF outBounds) {
            outBounds.setEmpty();
            if (pose == null) return;
            Rect sourceBounds = companionSourceBoundsForPose(pose);
            if (sourceBounds.isEmpty()) return;
            float width = sourceBounds.width() * companionPosePixelScale;
            float height = sourceBounds.height() * companionPosePixelScale;
            if (width <= 0f || height <= 0f) return;
            float centerFraction = COMPANION_STANDING_ANCHOR_X_FRACTION;
            float floorFraction = COMPANION_STANDING_FLOOR_Y_FRACTION;
            float top;
            if (pose == CompanionPageState.Pose.SLEEPING) {
                centerFraction = COMPANION_SLEEPING_ANCHOR_X_FRACTION;
                float centerY = imageBounds.top
                        + imageBounds.height() * COMPANION_SLEEPING_CENTER_Y_FRACTION;
                top = centerY - height / 2f;
            } else if (pose == CompanionPageState.Pose.EATING) {
                centerFraction += COMPANION_EATING_OFFSET_X_FRACTION;
                top = imageBounds.top + imageBounds.height() * floorFraction - height;
            } else if (pose == CompanionPageState.Pose.CUDDLE) {
                floorFraction += COMPANION_CUDDLE_OFFSET_Y_FRACTION;
                top = imageBounds.top + imageBounds.height() * floorFraction - height;
            } else {
                top = imageBounds.top + imageBounds.height() * floorFraction - height;
            }
            float centerX = imageBounds.left + imageBounds.width() * centerFraction;
            outBounds.set(
                    centerX - width / 2f,
                    top,
                    centerX + width / 2f,
                    top + height);
            if (outBounds.left < scene.left) outBounds.offset(scene.left - outBounds.left, 0f);
            if (outBounds.right > scene.right) outBounds.offset(scene.right - outBounds.right, 0f);
            if (outBounds.top < scene.top) outBounds.offset(0f, scene.top - outBounds.top);
            if (outBounds.bottom > scene.bottom) {
                outBounds.offset(0f, scene.bottom - outBounds.bottom);
            }
        }

        private void drawCompanionIsometricLamb(
                Canvas c,
                RectF lambBounds,
                CompanionPageState.Pose pose) {
            Bitmap lamb = companionBitmapForPose(pose);
            Rect src = companionSourceBoundsForPose(pose);
            if (lamb == null || pose == null || lambBounds.isEmpty() || src.isEmpty()) return;
            bitmapSrc.set(src);
            c.drawBitmap(lamb, bitmapSrc, lambBounds, bitmapPaint);
        }

        private void drawCompanionTreatBowl(
                Canvas c,
                RectF scene,
                RectF lambBounds,
                CompanionPageState.Pose pose) {
            if (companionTreatBowlBmp == null || pose != CompanionPageState.Pose.EATING) return;
            Rect sourceBounds = companionTreatBowlSourceBounds();
            if (sourceBounds.isEmpty()) return;
            float gap = Math.max(dp(2), lambBounds.width() * COMPANION_PROP_GAP_FRACTION);
            float preferredWidth = Math.min(dp(COMPANION_BOWL_MAX_WIDTH_DP),
                    lambBounds.width() * COMPANION_BOWL_WIDTH_FRACTION);
            float rightSpace = scene.right - lambBounds.right - gap;
            float leftSpace = lambBounds.left - scene.left - gap;
            boolean placeRight = rightSpace >= preferredWidth || leftSpace < preferredWidth;
            float width = Math.min(preferredWidth,
                    Math.max(0f, placeRight ? rightSpace : leftSpace));
            float height = width * sourceBounds.height() / (float) sourceBounds.width();
            if (width <= 0f || height <= 0f) return;
            float visualFloorY = lambBounds.bottom;
            RectF bowlBounds = companionTreatBowlDrawBounds;
            float left = placeRight
                    ? lambBounds.right + gap
                    : lambBounds.left - gap - width;
            bowlBounds.set(
                    left,
                    visualFloorY - height * .92f,
                    left + width,
                    visualFloorY + height * .08f);
            if (bowlBounds.left < scene.left) bowlBounds.offset(scene.left - bowlBounds.left, 0f);
            if (bowlBounds.right > scene.right) bowlBounds.offset(scene.right - bowlBounds.right, 0f);
            if (bowlBounds.top < scene.top) bowlBounds.offset(0f, scene.top - bowlBounds.top);
            if (bowlBounds.bottom > scene.bottom) {
                bowlBounds.offset(0f, scene.bottom - bowlBounds.bottom);
            }
            bitmapSrc.set(sourceBounds);
            c.drawBitmap(companionTreatBowlBmp, bitmapSrc, bowlBounds, bitmapPaint);
        }

        private void drawCompanionPlayBall(
                Canvas c,
                RectF scene,
                RectF lambBounds,
                float progress) {
            if (companionPlayBallBmp == null) return;
            Rect sourceBounds = companionPlayBallSourceBounds();
            if (sourceBounds.isEmpty()) return;
            float width = Math.min(
                    Math.min(
                            dp(COMPANION_BALL_MAX_WIDTH_DP),
                            lambBounds.width() * COMPANION_BALL_WIDTH_FRACTION),
                    Math.min(scene.width(), scene.height()));
            float height = width * sourceBounds.height() / (float) sourceBounds.width();
            if (width <= 0f || height <= 0f) return;
            float gap = Math.max(dp(2), lambBounds.width() * COMPANION_PROP_GAP_FRACTION);
            float minCenterX = scene.left + width / 2f;
            float maxCenterX = scene.right - width / 2f;
            float startX = Math.max(
                    minCenterX,
                    Math.min(maxCenterX, lambBounds.left - gap - width / 2f));
            float endX = Math.max(
                    minCenterX,
                    Math.min(maxCenterX,
                            startX - lambBounds.width() * COMPANION_BALL_TRAVEL_FRACTION));
            float centerX = startX + (endX - startX) * progress;
            float floorY = Math.max(
                    scene.top + height,
                    Math.min(scene.bottom, lambBounds.bottom + height * .04f));
            float bounce = (float) Math.abs(Math.sin(progress * Math.PI * 2f))
                    * height * COMPANION_BALL_BOUNCE_HEIGHT_FRACTION;
            RectF ballBounds = companionPlayBallDrawBounds;
            ballBounds.set(
                    centerX - width / 2f,
                    floorY - height - bounce,
                    centerX + width / 2f,
                    floorY - bounce);
            if (ballBounds.top < scene.top) ballBounds.offset(0f, scene.top - ballBounds.top);
            if (ballBounds.bottom > scene.bottom) {
                ballBounds.offset(0f, scene.bottom - ballBounds.bottom);
            }
            bitmapSrc.set(sourceBounds);
            c.drawBitmap(companionPlayBallBmp, bitmapSrc, ballBounds, bitmapPaint);
        }

        private void drawCompanionCuddleHearts(
                Canvas c,
                RectF lambBounds) {
            if (companionCuddleHeartBmp == null) return;
            Rect sourceBounds = companionCuddleHeartSourceBounds();
            if (sourceBounds.isEmpty()) return;
            int oldAlpha = bitmapPaint.getAlpha();
            RectF heartBounds = companionCuddleHeartDrawBounds;
            for (int i = 0; i < COMPANION_HEART_SIZE_FRACTIONS.length; i++) {
                float size = lambBounds.width() * COMPANION_HEART_SIZE_FRACTIONS[i];
                float centerX = lambBounds.centerX()
                        + lambBounds.width() * COMPANION_HEART_HORIZONTAL_OFFSETS[i];
                float centerY = lambBounds.top + lambBounds.height() * .28f
                        + lambBounds.height() * COMPANION_HEART_VERTICAL_OFFSETS[i];
                bitmapPaint.setAlpha(COMPANION_HEART_ALPHAS[i]);
                heartBounds.set(
                        centerX - size / 2f,
                        centerY - size / 2f,
                        centerX + size / 2f,
                        centerY + size / 2f);
                bitmapSrc.set(sourceBounds);
                c.drawBitmap(companionCuddleHeartBmp, bitmapSrc, heartBounds, bitmapPaint);
            }
            bitmapPaint.setAlpha(oldAlpha);
        }

        private Bitmap companionBitmapForPose(CompanionPageState.Pose pose) {
            if (pose == null) return null;
            switch (pose) {
                case EATING:
                    return companionArtworkEatingBmp;
                case CUDDLE:
                    return companionArtworkCuddleBmp;
                case HAPPY:
                    return companionArtworkHappyBmp;
                case IDLE:
                    return companionArtworkIdleBmp;
                case SLEEPING:
                    return companionArtworkSleepingBmp;
                default:
                    return null;
            }
        }

        private Rect companionSourceBoundsForPose(CompanionPageState.Pose pose) {
            if (pose == null) return companionEmptySrcBounds;
            Rect sourceBounds;
            switch (pose) {
                case EATING:
                    sourceBounds = companionArtworkEatingSrcBounds;
                    break;
                case CUDDLE:
                    sourceBounds = companionArtworkCuddleSrcBounds;
                    break;
                case HAPPY:
                    sourceBounds = companionArtworkHappySrcBounds;
                    break;
                case IDLE:
                    sourceBounds = companionArtworkIdleSrcBounds;
                    break;
                case SLEEPING:
                    sourceBounds = companionArtworkSleepingSrcBounds;
                    break;
                default:
                    return companionEmptySrcBounds;
            }
            int poseIndex = pose.ordinal();
            if (!companionPoseBoundsCaptured[poseIndex]) {
                captureCompanionVisibleBounds(companionBitmapForPose(pose), sourceBounds);
                companionPoseBoundsCaptured[poseIndex] = true;
            }
            return sourceBounds;
        }

        private Rect companionTreatBowlSourceBounds() {
            if (!companionTreatBowlBoundsCaptured) {
                captureCompanionVisibleBounds(companionTreatBowlBmp, companionTreatBowlSrcBounds);
                companionTreatBowlBoundsCaptured = true;
            }
            return companionTreatBowlSrcBounds;
        }

        private Rect companionPlayBallSourceBounds() {
            if (!companionPlayBallBoundsCaptured) {
                captureCompanionVisibleBounds(companionPlayBallBmp, companionPlayBallSrcBounds);
                companionPlayBallBoundsCaptured = true;
            }
            return companionPlayBallSrcBounds;
        }

        private Rect companionCuddleHeartSourceBounds() {
            if (!companionCuddleHeartBoundsCaptured) {
                captureCompanionVisibleBounds(
                        companionCuddleHeartBmp,
                        companionCuddleHeartSrcBounds);
                companionCuddleHeartBoundsCaptured = true;
            }
            return companionCuddleHeartSrcBounds;
        }

        private void drawCenteredFittedText(
                Canvas c,
                String value,
                float centerX,
                float baseline,
                float maxWidth,
                float startSize,
                float minSize,
                boolean bold) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(bold);
            float size = startSize;
            text.setTextSize(size);
            while (text.measureText(value) > maxWidth && size > minSize) {
                size -= dp(.3f);
                text.setTextSize(size);
            }
            c.drawText(ellipsizeText(value, maxWidth), centerX, baseline, text);
            text.setFakeBoldText(false);
        }

        private void drawCenteredFittedTextInArea(
                Canvas c,
                String value,
                RectF area,
                float startSize,
                float minSize,
                boolean bold) {
            if (area.width() <= 0f || area.height() <= 0f) return;
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(bold);
            float size = startSize;
            text.setTextSize(size);
            while (text.measureText(value) > area.width() && size > minSize) {
                size -= dp(.3f);
                text.setTextSize(size);
            }
            Paint.FontMetrics fm = text.getFontMetrics();
            float baseline = area.centerY() - (fm.ascent + fm.descent) / 2f;
            c.drawText(ellipsizeText(value, area.width()), area.centerX(), baseline, text);
            text.setFakeBoldText(false);
        }

        private String companionTitleText(CompanionPresentation.Snapshot snapshot) {
            if (snapshot != null && snapshot.displayName != null) {
                return snapshot.displayName;
            }
            return s(R.string.companion_title);
        }

        private String companionEnabledMessage(CompanionPresentation.Snapshot snapshot) {
            if (snapshot != null && snapshot.displayName != null) {
                return s(R.string.snackbar_companion_enabled_with_name, snapshot.displayName);
            }
            return s(R.string.snackbar_companion_enabled);
        }

        private String companionDisabledMessage(CompanionPresentation.Snapshot snapshot) {
            if (snapshot != null && snapshot.displayName != null) {
                return s(R.string.snackbar_companion_disabled_with_name, snapshot.displayName);
            }
            return s(R.string.snackbar_companion_disabled);
        }

        private void openCompanionNameEditor() {
            Context context = getContext();
            CompanionUiState.ViewState current = companionUiState;
            if (!(context instanceof Activity) || !current.available || !current.presentation.enabled) {
                showSnackbar(s(R.string.snackbar_companion_name_unavailable));
                return;
            }

            Activity activity = (Activity) context;
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            int padding = Math.round(dp(20));
            content.setPadding(padding, Math.round(dp(16)), padding, Math.round(dp(12)));

            TextView title = new TextView(activity);
            title.setText(R.string.companion_name_dialog_title);
            title.setTextColor(primaryText);
            title.setTextSize(22f);
            content.addView(title, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView subtitle = new TextView(activity);
            subtitle.setText(R.string.companion_name_dialog_subtitle);
            subtitle.setTextColor(secondaryText);
            subtitle.setTextSize(13f);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            subtitleParams.topMargin = Math.round(dp(6));
            content.addView(subtitle, subtitleParams);

            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setTextColor(primaryText);
            input.setHintTextColor(secondaryText);
            input.setTextSize(16f);
            input.setHint(R.string.companion_name_hint);
            input.setText(current.presentation.displayName == null ? "" : current.presentation.displayName);
            input.setSelection(input.getText().length());
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            inputParams.topMargin = Math.round(dp(14));
            content.addView(input, inputParams);

            TextView error = new TextView(activity);
            error.setTextColor(red);
            error.setTextSize(12f);
            error.setVisibility(View.GONE);
            LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            errorParams.topMargin = Math.round(dp(8));
            content.addView(error, errorParams);

            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.addView(content);

            AlertDialog dialog = new AlertDialog.Builder(activity, pdfDialogTheme())
                    .setView(scroll)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setNeutralButton(R.string.action_clear, null)
                    .setPositiveButton(R.string.action_save, null)
                    .create();

            dialog.setOnShowListener(ignored -> {
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive != null) {
                    positive.setOnClickListener(view -> {
                        String value = input.getText() == null ? "" : input.getText().toString();
                        try {
                            CompanionRepository.MutationResult result =
                                    companionRepository.setCompanionName(value);
                            if (result.status == CompanionRepository.MutationStatus.UNAVAILABLE) {
                                showSnackbar(s(R.string.snackbar_companion_name_unavailable));
                                dialog.dismiss();
                                return;
                            }
                            companionUiState = loadCompanionUiState();
                            showSnackbar(
                                    companionUiState.presentation.displayName == null
                                            ? s(R.string.snackbar_companion_name_cleared)
                                            : s(R.string.snackbar_companion_name_saved));
                            dialog.dismiss();
                            invalidate();
                        } catch (IllegalArgumentException ex) {
                            error.setText(R.string.snackbar_companion_name_invalid);
                            error.setVisibility(View.VISIBLE);
                        } catch (RuntimeException ex) {
                            showSnackbar(s(R.string.snackbar_companion_name_unavailable));
                            dialog.dismiss();
                        }
                    });
                }

                Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (neutral != null) {
                    neutral.setOnClickListener(view -> {
                        try {
                            CompanionRepository.MutationResult result =
                                    companionRepository.setCompanionName("");
                            if (result.status == CompanionRepository.MutationStatus.UNAVAILABLE) {
                                showSnackbar(s(R.string.snackbar_companion_name_unavailable));
                                dialog.dismiss();
                                return;
                            }
                            companionUiState = loadCompanionUiState();
                            showSnackbar(s(R.string.snackbar_companion_name_cleared));
                            dialog.dismiss();
                            invalidate();
                        } catch (RuntimeException ex) {
                            showSnackbar(s(R.string.snackbar_companion_name_unavailable));
                            dialog.dismiss();
                        }
                    });
                }
            });
            dialog.show();
        }

        private void performCompanionInteraction(
                CompanionInteractionCatalog.Entry interaction) {
            CompanionUiState.ViewState current = companionUiState;
            if (interaction == null
                    || !current.available
                    || !current.presentation.enabled) {
                clearCompanionPageReaction();
                showSnackbar(s(R.string.snackbar_companion_interaction_unavailable));
                return;
            }

            long interactionStartedMs = android.os.SystemClock.uptimeMillis();
            if (companionPageState.interactionsLocked(interactionStartedMs)) return;

            if (!CompanionAffordability.isAffordable(
                    current.presentation.balance,
                    interaction.cost)) {
                showSnackbar(s(R.string.snackbar_companion_interaction_insufficient));
                invalidate();
                return;
            }

            if (!companionPageState.tryBeginInteraction(
                    interaction.id,
                    interactionStartedMs)) {
                return;
            }
            cancelCompanionPageRedraw();
            invalidate();

            CompanionRepository.MutationResult result;
            try {
                result = companionRepository.purchaseInteraction(
                        interaction.id,
                        interaction.cost,
                        Instant.now());
            } catch (RuntimeException ignored) {
                result = null;
            }

            CompanionUiState.InteractionOutcome outcome =
                    CompanionUiState.classifyInteractionPurchase(result);
            companionUiState = loadCompanionUiState();
            if (!companionUiState.available || !companionUiState.presentation.enabled) {
                companionPageState.cancelInteraction(interaction.id);
                clearCompanionPageReaction();
                companionPageState.close();
                releaseCompanionPageAssets();
                showSnackbar(s(R.string.snackbar_companion_interaction_unavailable));
                invalidate();
                return;
            }
            if (outcome == CompanionUiState.InteractionOutcome.SUCCESS) {
                if (completeCompanionReaction(interaction.id)) {
                    showSnackbar(s(R.string.snackbar_companion_interaction_success));
                }
            } else if (outcome
                    == CompanionUiState.InteractionOutcome.INSUFFICIENT_BALANCE) {
                companionPageState.cancelInteraction(interaction.id);
                scheduleCompanionPageRedraw(android.os.SystemClock.uptimeMillis());
                showSnackbar(s(R.string.snackbar_companion_interaction_insufficient));
            } else {
                companionPageState.cancelInteraction(interaction.id);
                scheduleCompanionPageRedraw(android.os.SystemClock.uptimeMillis());
                showSnackbar(s(R.string.snackbar_companion_interaction_unavailable));
            }
            invalidate();
        }

        private void performCompanionPageInteraction(CompanionInteractionCatalog.Entry interaction) {
            if (!companionPageState.isOpen()) return;
            performCompanionInteraction(interaction);
        }

        private void drawSettingsSelector(Canvas c, RectF bounds, String label, String value, int menuId) {
            boolean active = settingsOpenMenu == menuId;
            boolean pressed = settingsPressedMenu == menuId;
            p.setStyle(Paint.Style.FILL);
            p.setColor(active || pressed
                    ? alpha(accent, dark ? 48 : 26)
                    : (dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 125)));
            c.drawRoundRect(bounds, dp(15), dp(15), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(active || pressed ? accent : outline);
            c.drawRoundRect(bounds, dp(15), dp(15), p);

            float chevronWidth = dp(24);
            float textWidth = bounds.width() - dp(16) - dp(16) - chevronWidth - dp(8);

            text.setTextAlign(startAlign());
            text.setFakeBoldText(true);
            text.setTextSize(dp(14));
            text.setColor(primaryText);
            c.drawText(label, startX(bounds, dp(16)), bounds.centerY() - dp(3), text);

            text.setFakeBoldText(false);
            text.setTextSize(dp(10.5f));
            text.setColor(secondaryText);
            c.drawText(ellipsizeText(value, textWidth), startX(bounds, dp(16)), bounds.centerY() + dp(15), text);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(17));
            text.setColor(accent);
            c.drawText(active ? "\u2303" : "\u2304", endX(bounds, dp(22)), bounds.centerY() + dp(6), text);
        }

        private float layoutSettingsSelectorMenu(
                Canvas c,
                RectF viewport,
                float left,
                float right,
                float top,
                int menuId,
                boolean draw) {
            String[] optionLabels = settingsMenuOptionLabels(menuId);
            int selectedIndex = settingsSelectedOptionIndex(menuId);
            float rowHeight = dp(SETTINGS_MENU_ROW_HEIGHT_DP);
            float rowGap = dp(SETTINGS_MENU_ROW_GAP_DP);
            float panelTop = top + dp(SETTINGS_MENU_TOP_GAP_DP);
            float panelBottom = panelTop + dp(6) + dp(6)
                    + optionLabels.length * rowHeight
                    + Math.max(0, optionLabels.length - 1) * rowGap;
            RectF panel = new RectF(left, panelTop, right, panelBottom);
            if (draw) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark ? alpha(Color.BLACK, 34) : alpha(Color.WHITE, 112));
                c.drawRoundRect(panel, dp(16), dp(16), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(outline);
                c.drawRoundRect(panel, dp(16), dp(16), p);
            }

            float y = panel.top + dp(6);
            for (int i = 0; i < optionLabels.length; i++) {
                RectF row = new RectF(panel.left + dp(6), y, panel.right - dp(6), y + rowHeight);
                if (draw) {
                    final int optionIndex = i;
                    drawSelectionRow(c, row, optionLabels[i], selectedIndex == i);
                    addVisibleTouchTarget(viewport, row, () -> selectSettingsMenuOption(menuId, optionIndex));
                }
                y = row.bottom + rowGap;
            }
            return panel.bottom + dp(SETTINGS_MENU_BOTTOM_GAP_DP);
        }

        private String[] settingsMenuOptionLabels(int menuId) {
            if (menuId == SETTINGS_MENU_LANGUAGE) {
                return new String[] {
                        s(R.string.language_system),
                        s(R.string.language_german),
                        s(R.string.language_english),
                        s(R.string.language_spanish),
                        s(R.string.language_french),
                        s(R.string.language_portuguese_portugal),
                        s(R.string.language_turkish),
                        s(R.string.language_arabic),
                        s(R.string.language_polish),
                        s(R.string.language_russian),
                        s(R.string.language_ukrainian),
                        s(R.string.language_romanian)
                };
            }
            return new String[] {
                    s(R.string.theme_system),
                    combinedThemeOptionLabel(R.string.style_standard, R.string.theme_light),
                    combinedThemeOptionLabel(R.string.style_standard, R.string.theme_dark),
                    combinedThemeOptionLabel(R.string.style_pastel_cozy, R.string.theme_light),
                    combinedThemeOptionLabel(R.string.style_pastel_cozy, R.string.theme_dark)
            };
        }

        private int settingsSelectedOptionIndex(int menuId) {
            if (menuId == SETTINGS_MENU_LANGUAGE) {
                return AppLanguage.indexOf(uiPrefs.getString(
                        AppLanguage.PREFERENCE_KEY, AppLanguage.SYSTEM));
            }
            return currentThemeSelectionIndex();
        }

        private void toggleSettingsMenu(int menuId) {
            settingsOpenMenu = settingsOpenMenu == menuId ? SETTINGS_MENU_NONE : menuId;
            settingsPressedMenu = SETTINGS_MENU_NONE;
        }

        private void selectSettingsMenuOption(int menuId, int optionIndex) {
            if (menuId == SETTINGS_MENU_LANGUAGE) {
                selectLanguage(optionIndex);
                return;
            }
            selectThemeOption(optionIndex);
        }

        private void addVisibleTouchTarget(RectF viewport, RectF target, Runnable action) {
            RectF visible = new RectF(target);
            if (!visible.intersect(viewport)) return;
            targets.add(new TouchTarget(visible, action));
        }

        private void openSettingsOverlay() {
            settingsOpen = true;
            aboutOpen = false;
            changelogOpen = false;
            licenseOpen = false;
            portionHelpOpen = false;
            backupOptionsOpen = false;
            restoreWarningOpen = false;
            settingsOpenMenu = SETTINGS_MENU_NONE;
            settingsPressedMenu = SETTINGS_MENU_NONE;
            settingsScrollY = 0f;
            settingsMaxScrollY = 0f;
            settingsLastBodyW = -1f;
            settingsLastBodyH = -1f;
            settingsLastContentH = -1f;
            settingsScrollArea.setEmpty();
            settingsLanguageSelectorRect.setEmpty();
            settingsThemeSelectorRect.setEmpty();
            clearSettingsBodyTouchState();
        }

        private void closeSettingsOverlay() {
            settingsOpen = false;
            settingsOpenMenu = SETTINGS_MENU_NONE;
            settingsScrollY = 0f;
            settingsMaxScrollY = 0f;
            settingsLastBodyW = -1f;
            settingsLastBodyH = -1f;
            settingsLastContentH = -1f;
            settingsScrollArea.setEmpty();
            settingsLanguageSelectorRect.setEmpty();
            settingsThemeSelectorRect.setEmpty();
            clearSettingsBodyTouchState();
        }

        private void openAboutOverlayFromSettings() {
            closeSettingsOverlay();
            invalidate();
            Context context = getContext();
            context.startActivity(WelcomeActivity.createAboutIntent(context));
        }

        private void drawSelectionRow(Canvas c, RectF row, String label, boolean selected) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(selected ? alpha(accent, dark ? 70 : 38) : (dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 120)));
            c.drawRoundRect(row, dp(13), dp(13), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(selected ? accent : outline);
            c.drawRoundRect(row, dp(13), dp(13), p);
            text.setTextAlign(startAlign());
            text.setTextSize(dp(13.5f));
            text.setFakeBoldText(selected);
            text.setColor(primaryText);
            c.drawText(ellipsizeText(label, row.width() - dp(54)), startX(row, dp(14)), row.centerY() + dp(5), text);
            text.setFakeBoldText(false);
            if (selected) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(accent);
                c.drawCircle(endX(row, dp(21)), row.centerY(), dp(6.5f), p);
            }
        }

        private void selectLanguage(int index) {
            String tag = AppLanguage.tagAt(index);
            settingsOpenMenu = SETTINGS_MENU_NONE;
            settingsPressedMenu = SETTINGS_MENU_NONE;
            if (!uiPrefs.edit().putString(AppLanguage.PREFERENCE_KEY, tag).commit()) return;
            Context context = getContext();
            if (context instanceof Activity) ((Activity) context).recreate();
        }

        private String currentLanguageLabel() {
            int[] labels = {
                    R.string.language_system, R.string.language_german, R.string.language_english,
                    R.string.language_spanish, R.string.language_french,
                    R.string.language_portuguese_portugal, R.string.language_turkish,
                    R.string.language_arabic, R.string.language_polish,
                    R.string.language_russian, R.string.language_ukrainian,
                    R.string.language_romanian
            };
            return s(labels[AppLanguage.indexOf(uiPrefs.getString(
                    AppLanguage.PREFERENCE_KEY, AppLanguage.SYSTEM))]);
        }

        private int currentThemeSelectionIndex() {
            if (themeMode == MODE_SYSTEM) return 0;
            if (skinStyle == SKIN_STANDARD) {
                return themeMode == MODE_DARK ? 2 : 1;
            }
            return themeMode == MODE_DARK ? 4 : 3;
        }

        private String currentThemeLabel() {
            String[] labels = settingsMenuOptionLabels(SETTINGS_MENU_THEME);
            return labels[currentThemeSelectionIndex()];
        }

        private String combinedThemeOptionLabel(int styleLabel, int themeLabel) {
            return s(styleLabel) + " " + s(themeLabel);
        }

        private void selectThemeOption(int index) {
            int nextThemeMode = themeMode;
            int nextSkinStyle = skinStyle;
            switch (index) {
                case 1:
                    nextThemeMode = MODE_LIGHT;
                    nextSkinStyle = SKIN_STANDARD;
                    break;
                case 2:
                    nextThemeMode = MODE_DARK;
                    nextSkinStyle = SKIN_STANDARD;
                    break;
                case 3:
                    nextThemeMode = MODE_LIGHT;
                    nextSkinStyle = SKIN_PASTEL_COZY;
                    break;
                case 4:
                    nextThemeMode = MODE_DARK;
                    nextSkinStyle = SKIN_PASTEL_COZY;
                    break;
                default:
                    nextThemeMode = MODE_SYSTEM;
                    break;
            }

            boolean changed = nextThemeMode != themeMode || nextSkinStyle != skinStyle;
            themeMode = nextThemeMode;
            if (index != 0) {
                skinStyle = nextSkinStyle;
            }
            settingsOpenMenu = SETTINGS_MENU_NONE;
            settingsPressedMenu = SETTINGS_MENU_NONE;
            if (!changed) return;

            SharedPreferences.Editor editor = prefs.edit().putInt("theme_mode", themeMode);
            if (index != 0) {
                editor.putInt("skin_style", skinStyle);
            }
            editor.apply();
            showSnackbar(s(R.string.snackbar_theme_saved));
        }

        private void drawPortionHelpOverlay(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 145 : 90));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, () -> { }));

            float margin = dp(22);
            float safeTop = dp(34);
            float safeBottom = h - systemBottomInset() - dp(24);
            float maxPanelH = Math.max(dp(1), safeBottom - safeTop);
            float fixedTopH = dp(90);
            float fixedBottomH = dp(124);
            float bodyW = w - margin * 2f - dp(44);
            float contentH = measurePortionInfoBodyHeight(bodyW);
            float desiredPanelH = fixedTopH + contentH + fixedBottomH;
            float panelH = Math.min(desiredPanelH, maxPanelH);
            panelH = Math.max(Math.min(fixedTopH + fixedBottomH, maxPanelH), panelH);
            float top = safeTop + Math.max(0f, (maxPanelH - panelH) / 2f);
            RectF panel = new RectF(margin, top, w - margin, top + panelH);
            drawCard(c, panel);

            RectF close = new RectF(panel.right - dp(46), panel.top + dp(10), panel.right - dp(10), panel.top + dp(46));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText("\u00d7", close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, () -> portionHelpOpen = false));

            float y = panel.top + dp(34);
            text.setFakeBoldText(true);
            text.setTextSize(dp(21));
            text.setColor(accent);
            c.drawText(s(R.string.portion_help_title), panel.centerX(), y, text);
            text.setFakeBoldText(false);
            y += dp(25);

            text.setTextSize(dp(11));
            text.setColor(secondaryText);
            c.drawText(s(R.string.portion_help_subtitle), panel.centerX(), y, text);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawLine(panel.left + dp(22), panel.top + dp(74), panel.right - dp(22), panel.top + dp(74), p);

            RectF source = new RectF(panel.left + dp(38), panel.bottom - dp(108), panel.right - dp(38), panel.bottom - dp(68));
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 110));
            c.drawRoundRect(source, dp(15), dp(15), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(source, dp(15), dp(15), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(12.2f));
            text.setColor(primaryText);
            c.drawText(s(R.string.settings_bzfe_source), source.centerX(), source.centerY() + dp(5), text);
            targets.add(new TouchTarget(source, this::openBzfeSourceLink));

            RectF ok = new RectF(panel.left + dp(38), panel.bottom - dp(58), panel.right - dp(38), panel.bottom - dp(18));
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(accent, 55) : alpha(accent, 38));
            c.drawRoundRect(ok, dp(15), dp(15), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(accent);
            c.drawRoundRect(ok, dp(15), dp(15), p);

            text.setFakeBoldText(true);
            text.setTextSize(dp(13));
            text.setColor(dark ? Color.rgb(223, 246, 209) : accent);
            c.drawText(s(R.string.action_understood), ok.centerX(), ok.centerY() + dp(5), text);
            text.setFakeBoldText(false);
            targets.add(new TouchTarget(ok, () -> portionHelpOpen = false));

            RectF body = new RectF(panel.left + dp(22), panel.top + fixedTopH, panel.right - dp(22), source.top - dp(16));
            portionInfoScrollArea.set(body);

            contentH = measurePortionInfoBodyHeight(body.width());
            portionInfoMaxScrollY = Math.max(0f, contentH - body.height());
            if (Math.abs(body.width() - portionInfoLastBodyW) > .5f
                    || Math.abs(body.height() - portionInfoLastBodyH) > .5f
                    || Math.abs(contentH - portionInfoLastContentH) > .5f) {
                portionInfoScrollY = 0f;
                portionInfoLastBodyW = body.width();
                portionInfoLastBodyH = body.height();
                portionInfoLastContentH = contentH;
            }
            portionInfoScrollY = clampOverlayScroll(portionInfoScrollY, portionInfoMaxScrollY);
            y = body.top + dp(14) - portionInfoScrollY;

            int save = c.save();
            c.clipRect(body);
            drawPortionInfoBody(c, body, y);
            c.restoreToCount(save);

            drawOverlayScrollIndicator(c, body, portionInfoScrollY, portionInfoMaxScrollY);
        }

        private float measurePortionInfoBodyHeight(float width) {
            float y = dp(14);
            text.setTextSize(dp(11.4f));
            y += measureWrappedTextHeight(
                    s(R.string.portion_help_intro),
                    width,
                    dp(16));
            y += dp(8);

            text.setTextSize(dp(10.8f));
            float bulletTextW = Math.max(dp(40), width - dp(18));
            String[] entries = a(R.array.portion_help_bullets);
            for (String entry : entries) {
                y += measureWrappedBulletHeight(entry, bulletTextW, dp(15));
                y += dp(2);
            }

            y += dp(6);
            text.setTextSize(dp(10.1f));
            y += measureWrappedTextHeight(
                    s(R.string.portion_help_source_note),
                    width,
                    dp(14.2f));
            return y + dp(8);
        }

        private void drawPortionInfoBody(Canvas c, RectF body, float y) {
            text.setTextAlign(startAlign());
            text.setColor(primaryText);
            text.setTextSize(dp(11.4f));
            y = drawWrappedText(c,
                    s(R.string.portion_help_intro),
                    startX(body, 0f),
                    y,
                    body.width(),
                    dp(16));
            y += dp(8);

            String[] entries = a(R.array.portion_help_bullets);

            text.setTextSize(dp(10.8f));
            float bulletX = startX(body, dp(4));
            float textX = startX(body, dp(18));
            float maxTextWidth = body.width() - dp(18);
            for (String entry : entries) {
                y = drawWrappedBullet(c, entry, bulletX, textX, y, maxTextWidth, dp(15));
                y += dp(2);
            }

            y += dp(6);
            text.setTextSize(dp(10.1f));
            text.setColor(secondaryText);
            drawWrappedText(c,
                    s(R.string.portion_help_source_note),
                    startX(body, 0f),
                    y,
                    body.width(),
                    dp(14.2f));
        }

        private float drawPortionGroup(Canvas c, float left, float top, float right, int category, String title, String portions, String[] lines, float height, float scale) {
            RectF r = new RectF(left, top, right, top + height);
            int col = categoryColor(category);

            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 14) : alpha(Color.WHITE, 112));
            c.drawRoundRect(r, dp(11), dp(11), p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(0.85f));
            p.setColor(alpha(categoryBorder(category), dark ? 130 : 155));
            c.drawRoundRect(r, dp(11), dp(11), p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            RectF strip = new RectF(r.left, r.top, r.left + dp(6), r.bottom);
            c.drawRoundRect(strip, dp(9), dp(9), p);
            c.drawRect(strip.left + dp(3), strip.top, strip.right, strip.bottom, p);

            float iconR = dp(12) * scale;
            float iconCx = r.left + dp(24);
            float iconCy = r.top + Math.min(dp(25) * scale, r.height() / 2f);
            c.drawCircle(iconCx, iconCy, iconR, p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(8.6f) * scale);
            text.setColor(dark ? Color.rgb(44, 42, 38) : Color.WHITE);
            String letter = title.length() > 0 ? title.substring(0, 1) : "?";
            c.drawText(letter, iconCx, iconCy + dp(3.2f) * scale, text);

            float titleX = r.left + dp(44);
            float detailX = r.left + Math.max(dp(145), r.width() * .42f);
            float baseY = r.top + dp(20) * scale;
            text.setTextAlign(Paint.Align.LEFT);
            text.setFakeBoldText(true);
            text.setTextSize(fitTextSize(title, dp(12.4f) * scale, dp(9.2f) * scale, Math.max(dp(70), detailX - titleX - dp(8))));
            text.setColor(primaryText);
            c.drawText(title, titleX, baseY, text);

            text.setFakeBoldText(true);
            text.setTextSize(dp(10.5f) * scale);
            text.setColor(categoryBorder(category));
            c.drawText(portions, detailX, baseY, text);
            text.setFakeBoldText(false);

            text.setTextSize(dp(8.9f) * scale);
            text.setColor(primaryText);
            float y = baseY + dp(14) * scale;
            float maxW = r.right - dp(8) - detailX;
            for (String line : lines) {
                if (text.measureText(line) > maxW) {
                    y = drawWrappedText(c, line, detailX, y, maxW, dp(10.6f) * scale);
                } else {
                    c.drawText(line, detailX, y, text);
                    y += dp(11.8f) * scale;
                }
            }
            return r.bottom;
        }

        private float fitTextSize(String value, float startSize, float minSize, float maxWidth) {
            float size = startSize;
            text.setTextSize(size);
            while (text.measureText(value) > maxWidth && size > minSize) {
                size -= dp(.5f);
                text.setTextSize(size);
            }
            return size;
        }
        private void drawAboutOverlay(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 120 : 75));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, () -> { }));

            float margin = dp(24);
            float avatarSize = changelogOpen ? dp(84) : dp(90);
            float headerTopPad = dp(22);
            float yGap = dp(12);
            float versionH = dp(42);
            float logH = changelogOpen ? dp(214) : 0;
            float privacyH = changelogOpen ? 0 : dp(48);
            float licenseH = dp(42);
            float supportH = dp(44);
            float footerH = dp(22);

            float desiredH = headerTopPad
                    + avatarSize
                    + dp(18)
                    + dp(28)
                    + dp(24)
                    + dp(17)
                    + dp(24)
                    + versionH
                    + yGap
                    + logH
                    + (changelogOpen ? yGap : 0)
                    + privacyH
                    + (privacyH > 0 ? yGap : 0)
                    + licenseH
                    + yGap
                    + supportH
                    + footerH
                    + dp(22);

            float maxPanelH = h - dp(120);
            float panelH = Math.min(desiredH, maxPanelH);
            float panelTop = Math.max(dp(54), (h - panelH) / 2f);
            RectF panel = new RectF(margin, panelTop, w - margin, panelTop + panelH);
            drawCard(c, panel);

            RectF close = new RectF(panel.right - dp(46), panel.top + dp(10), panel.right - dp(10), panel.top + dp(46));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText("\u00d7", close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, () -> {
                aboutOpen = false;
                changelogOpen = false;
                licenseOpen = false;
            }));

            float y = panel.top + headerTopPad;
            RectF avatar = new RectF(panel.centerX() - avatarSize / 2f, y, panel.centerX() + avatarSize / 2f, y + avatarSize);
            drawAboutBrand(c, avatar);
            y = avatar.bottom + dp(20);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(22));
            text.setColor(accent);
            c.drawText(s(R.string.app_name), panel.centerX(), y, text);
            text.setFakeBoldText(false);
            y += dp(26);

            text.setTextSize(dp(12));
            text.setColor(secondaryText);
            c.drawText(s(R.string.about_tagline), panel.centerX(), y, text);
            y += dp(17);
            text.setTextSize(dp(10.5f));
            c.drawText(s(R.string.about_credit), panel.centerX(), y, text);
            y += dp(24);

            RectF version = new RectF(panel.left + dp(28), y, panel.right - dp(28), y + versionH);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 125));
            c.drawRoundRect(version, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(version, dp(14), dp(14), p);
            text.setTextSize(dp(13));
            text.setColor(primaryText);
            c.drawText(appVersionLabel(), version.centerX() - dp(8), version.centerY() + dp(5), text);
            text.setTextSize(dp(17));
            text.setColor(accent);
            c.drawText(changelogOpen ? "\u2303" : "\u2304", version.right - dp(24), version.centerY() + dp(6), text);
            targets.add(new TouchTarget(version, () -> changelogOpen = !changelogOpen));
            y = version.bottom + yGap;

            if (changelogOpen) {
                RectF log = new RectF(panel.left + dp(28), y, panel.right - dp(28), Math.min(panel.bottom - dp(104), y + logH));
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark ? alpha(Color.BLACK, 32) : alpha(Color.WHITE, 96));
                c.drawRoundRect(log, dp(16), dp(16), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(outline);
                c.drawRoundRect(log, dp(16), dp(16), p);

                text.setTextAlign(Paint.Align.CENTER);
                text.setFakeBoldText(true);
                text.setTextSize(dp(17));
                text.setColor(primaryText);
                c.drawText(s(R.string.about_changelog), log.centerX(), log.top + dp(30), text);
                text.setFakeBoldText(false);

                RectF logClose = new RectF(log.right - dp(42), log.top + dp(6), log.right - dp(8), log.top + dp(40));
                text.setTextSize(dp(22));
                text.setColor(secondaryText);
                c.drawText("\u00d7", logClose.centerX(), logClose.centerY() + dp(7), text);
                targets.add(new TouchTarget(logClose, () -> changelogOpen = false));

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(outline);
                c.drawLine(log.left + dp(18), log.top + dp(43), log.right - dp(18), log.top + dp(43), p);

                text.setTextAlign(Paint.Align.LEFT);
                text.setTextSize(dp(12));
                text.setColor(accent);
                text.setFakeBoldText(true);
                c.drawText(appVersionLabel(), log.left + dp(22), log.top + dp(65), text);
                text.setFakeBoldText(false);

                String[] entries = a(R.array.about_changelog_entries);
                text.setTextSize(dp(10.5f));
                text.setColor(primaryText);
                float ey = log.top + dp(88);
                float bulletX = log.left + dp(25);
                float textX = log.left + dp(38);
                float maxTextWidth = log.right - dp(24) - textX;
                for (String entry : entries) {
                    ey = drawWrappedBullet(c, entry, bulletX, textX, ey, maxTextWidth, dp(14.5f));
                }
                y = log.bottom + yGap;
            } else {
                RectF privacy = new RectF(panel.left + dp(30), y, panel.right - dp(30), y + privacyH);
                p.setStyle(Paint.Style.FILL);
                p.setColor(dark ? alpha(Color.WHITE, 12) : alpha(Color.WHITE, 85));
                c.drawRoundRect(privacy, dp(14), dp(14), p);

                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(dp(10.5f));
                text.setColor(secondaryText);
                c.drawText(s(R.string.about_privacy_local), privacy.centerX(), privacy.top + dp(20), text);
                c.drawText(s(R.string.about_privacy_tracking), privacy.centerX(), privacy.top + dp(37), text);
                y = privacy.bottom + yGap;
            }

            RectF license = new RectF(panel.left + dp(28), y, panel.right - dp(28), y + licenseH);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 125));
            c.drawRoundRect(license, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(license, dp(14), dp(14), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(false);
            text.setTextSize(dp(13));
            text.setColor(primaryText);
            c.drawText(s(R.string.about_license), license.centerX() - dp(8), license.centerY() + dp(5), text);
            text.setTextSize(dp(17));
            text.setColor(accent);
            c.drawText("\u203a", license.right - dp(24), license.centerY() + dp(6), text);
            targets.add(new TouchTarget(license, () -> {
                licenseOpen = true;
                licenseScrollY = 0f;
                licenseMaxScrollY = 0f;
            }));
            y = license.bottom + yGap;

            RectF support = new RectF(panel.left + dp(42), y, panel.right - dp(42), y + supportH);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(accent, 45) : alpha(accent, 35));
            c.drawRoundRect(support, dp(16), dp(16), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.1f));
            p.setColor(dark ? alpha(accent, 210) : accent);
            c.drawRoundRect(support, dp(16), dp(16), p);

            float iconSize = dp(29);
            RectF kofi = new RectF(support.left + dp(20), support.centerY() - iconSize / 2f, support.left + dp(20) + iconSize, support.centerY() + iconSize / 2f);
            drawKoFiCupIcon(c, kofi);

            text.setTextAlign(Paint.Align.LEFT);
            text.setFakeBoldText(true);
            text.setTextSize(dp(13.5f));
            text.setColor(dark ? Color.rgb(223, 246, 209) : accent);
            drawFittedText(c, s(R.string.about_support), kofi.right + dp(14), support.centerY() + dp(5),
                    support.right - kofi.right - dp(20), dp(13.5f), dp(10.5f), true);
            text.setFakeBoldText(false);
            targets.add(new TouchTarget(support, this::openSupportLink));

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(10));
            text.setColor(secondaryText);
            c.drawText(s(R.string.about_thanks), panel.centerX(), support.bottom + dp(19), text);
        }

        private void drawLicenseOverlay(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 150 : 92));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, () -> { }));

            float margin = dp(22);
            float maxPanelH = h - systemBottomInset() - dp(72);
            float panelH = Math.min(dp(620), maxPanelH);
            float panelTop = Math.max(dp(36), (h - panelH) / 2f);
            RectF panel = new RectF(margin, panelTop, w - margin, panelTop + panelH);
            drawCard(c, panel);

            RectF close = new RectF(panel.right - dp(46), panel.top + dp(10), panel.right - dp(10), panel.top + dp(46));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText("\u00d7", close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, this::closeLicenseOverlay));

            float y = panel.top + dp(34);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(21));
            text.setColor(accent);
            c.drawText(s(R.string.license_title), panel.centerX(), y, text);
            text.setFakeBoldText(false);
            y += dp(26);

            text.setTextSize(dp(11.5f));
            text.setColor(secondaryText);
            c.drawText(s(R.string.license_subtitle), panel.centerX(), y, text);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawLine(panel.left + dp(22), panel.top + dp(76), panel.right - dp(22), panel.top + dp(76), p);

            RectF ok = new RectF(panel.left + dp(44), panel.bottom - dp(58), panel.right - dp(44), panel.bottom - dp(18));
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(accent, 55) : alpha(accent, 38));
            c.drawRoundRect(ok, dp(15), dp(15), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(accent);
            c.drawRoundRect(ok, dp(15), dp(15), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(13));
            text.setColor(dark ? Color.rgb(223, 246, 209) : accent);
            c.drawText(s(R.string.action_understood), ok.centerX(), ok.centerY() + dp(5), text);
            text.setFakeBoldText(false);
            targets.add(new TouchTarget(ok, this::closeLicenseOverlay));

            RectF body = new RectF(panel.left + dp(24), panel.top + dp(94), panel.right - dp(24), ok.top - dp(16));
            licenseScrollArea.set(body);
            float contentH = measureLicenseBodyHeight(body.width());
            licenseMaxScrollY = Math.max(0f, contentH - body.height());
            licenseScrollY = clampOverlayScroll(licenseScrollY, licenseMaxScrollY);
            y = body.top + dp(14) - licenseScrollY;

            int save = c.save();
            c.clipRect(body);
            drawLicenseBody(c, body, y);
            c.restoreToCount(save);

            drawOverlayScrollIndicator(c, body, licenseScrollY, licenseMaxScrollY);
        }

        private void closeLicenseOverlay() {
            licenseOpen = false;
            if (licenseOpenedFromFirstLaunch) {
                licenseOpenedFromFirstLaunch = false;
                firstLaunchNoticeOpen = true;
            }
        }

        private void drawFirstLaunchNotice(Canvas c, float w, float h) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 180 : 125));
            c.drawRect(0f, 0f, w, h, p);

            float margin = dp(20);
            float maxPanelH = h - systemBottomInset() - dp(48);
            float panelH = Math.min(dp(610), maxPanelH);
            float panelTop = Math.max(dp(24), (h - panelH) / 2f);
            RectF panel = new RectF(margin, panelTop, w - margin, panelTop + panelH);
            drawCard(c, panel);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(fitTextSize(
                    s(R.string.first_launch_title),
                    dp(20),
                    dp(15),
                    panel.width() - dp(40)));
            text.setColor(accent);
            c.drawText(s(R.string.first_launch_title), panel.centerX(), panel.top + dp(38), text);
            text.setFakeBoldText(false);

            text.setTextSize(dp(10.5f));
            text.setColor(secondaryText);
            c.drawText(s(R.string.first_launch_version_format, FIRST_LAUNCH_NOTICE_VERSION),
                    panel.centerX(), panel.top + dp(58), text);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawLine(panel.left + dp(20), panel.top + dp(74),
                    panel.right - dp(20), panel.top + dp(74), p);

            RectF license = new RectF(
                    panel.left + dp(24),
                    panel.bottom - dp(112),
                    panel.right - dp(24),
                    panel.bottom - dp(72));
            drawFirstLaunchAction(c, license, s(R.string.license_title), false);
            targets.add(new TouchTarget(license, () -> {
                licenseOpenedFromFirstLaunch = true;
                licenseOpen = true;
                licenseScrollY = 0f;
                licenseMaxScrollY = 0f;
            }));

            float actionGap = dp(10);
            float actionWidth = (license.width() - actionGap) / 2f;
            RectF reject = new RectF(
                    license.left,
                    panel.bottom - dp(58),
                    license.left + actionWidth,
                    panel.bottom - dp(18));
            RectF accept = new RectF(
                    reject.right + actionGap,
                    reject.top,
                    license.right,
                    reject.bottom);
            drawFirstLaunchAction(c, reject, s(R.string.action_reject), false);
            drawFirstLaunchAction(c, accept, s(R.string.action_accept), true);
            targets.add(new TouchTarget(reject, this::rejectFirstLaunchNotice));
            targets.add(new TouchTarget(accept, this::acceptFirstLaunchNotice));

            RectF body = new RectF(
                    panel.left + dp(24),
                    panel.top + dp(88),
                    panel.right - dp(24),
                    license.top - dp(14));
            firstLaunchScrollArea.set(body);
            float contentH = measureFirstLaunchBodyHeight(body.width());
            firstLaunchMaxScrollY = Math.max(0f, contentH - body.height());
            firstLaunchScrollY = clampOverlayScroll(firstLaunchScrollY, firstLaunchMaxScrollY);

            int save = c.save();
            c.clipRect(body);
            drawFirstLaunchBody(c, body, body.top + dp(13) - firstLaunchScrollY);
            c.restoreToCount(save);
            drawOverlayScrollIndicator(c, body, firstLaunchScrollY, firstLaunchMaxScrollY);
        }

        private void drawFirstLaunchAction(Canvas c, RectF action, String label, boolean primary) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(primary
                    ? alpha(accent, dark ? 70 : 45)
                    : (dark ? alpha(Color.WHITE, 18) : alpha(Color.WHITE, 125)));
            c.drawRoundRect(action, dp(13), dp(13), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(primary ? accent : outline);
            c.drawRoundRect(action, dp(13), dp(13), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(primary);
            text.setTextSize(dp(12.5f));
            text.setColor(primary && dark ? Color.rgb(223, 246, 209) : primaryText);
            c.drawText(label, action.centerX(), action.centerY() + dp(4.5f), text);
            text.setFakeBoldText(false);
        }

        private float measureFirstLaunchBodyHeight(float width) {
            float y = dp(13);
            text.setTextSize(dp(11.5f));
            y += measureWrappedTextHeight(
                    s(R.string.first_launch_intro),
                    width, dp(16));
            y += dp(9);
            text.setTextSize(dp(10.7f));
            String[] paragraphs = a(R.array.first_launch_paragraphs);
            for (String paragraph : paragraphs) {
                y += measureWrappedTextHeight(paragraph, width, dp(15));
                y += dp(7);
            }
            return y + dp(6);
        }

        private void drawFirstLaunchBody(Canvas c, RectF body, float y) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setFakeBoldText(true);
            text.setTextSize(dp(11.5f));
            text.setColor(primaryText);
            y = drawWrappedText(c,
                    s(R.string.first_launch_intro),
                    body.left, y, body.width(), dp(16));
            text.setFakeBoldText(false);
            y += dp(9);

            text.setTextSize(dp(10.7f));
            String[] paragraphs = a(R.array.first_launch_paragraphs);
            for (String paragraph : paragraphs) {
                y = drawWrappedText(c, paragraph, body.left, y, body.width(), dp(15));
                y += dp(7);
            }
        }

        private void acceptFirstLaunchNotice() {
            if (prefs.edit()
                    .putInt(FIRST_LAUNCH_ACK_VERSION_KEY, FIRST_LAUNCH_NOTICE_VERSION)
                    .commit()) {
                firstLaunchNoticeOpen = false;
                firstLaunchScrollY = 0f;
                firstLaunchMaxScrollY = 0f;
            }
        }

        private void rejectFirstLaunchNotice() {
            Context context = getContext();
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        }

        private float measureLicenseBodyHeight(float width) {
            float y = dp(14);
            text.setTextSize(dp(11.2f));
            y += measureWrappedTextHeight(s(R.string.license_translation_notice), width, dp(15.5f));
            y += dp(6);
            y += measureWrappedTextHeight(s(R.string.license_owner), width, dp(15.5f));
            y += dp(6);
            y += measureWrappedTextHeight(s(R.string.license_distribution), width, dp(15.5f));
            y += dp(6);
            y += measureWrappedTextHeight(s(R.string.license_channels), width, dp(15.5f));
            y += dp(12);
            y += dp(19);

            text.setTextSize(dp(10.7f));
            float bulletTextW = Math.max(dp(40), width - dp(18));
            String[] entries = a(R.array.license_restrictions);
            for (String entry : entries) {
                y += measureWrappedBulletHeight(entry, bulletTextW, dp(14.2f));
                y += dp(1.5f);
            }

            y += dp(5);
            text.setTextSize(dp(9.6f));
            y += measureWrappedTextHeight(
                    s(R.string.license_kofi),
                    width,
                    dp(13.2f));
            y += dp(5);
            y += measureWrappedTextHeight(
                    s(R.string.license_bzfe),
                    width,
                    dp(13.2f));
            y += dp(5);
            y += measureWrappedTextHeight(
                    s(R.string.license_rights),
                    width,
                    dp(13.2f));
            y += dp(5);
            y += measureWrappedTextHeight(
                    s(R.string.license_disclaimer),
                    width,
                    dp(13.2f));
            return y + dp(8);
        }

        private void drawLicenseBody(Canvas c, RectF body, float y) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(dp(11.2f));
            text.setColor(primaryText);
            y = drawWrappedText(c, s(R.string.license_translation_notice), body.left, y, body.width(), dp(15.5f));
            y += dp(6);
            y = drawWrappedText(c, s(R.string.license_owner), body.left, y, body.width(), dp(15.5f));
            y += dp(6);
            y = drawWrappedText(c, s(R.string.license_distribution), body.left, y, body.width(), dp(15.5f));
            y += dp(6);
            y = drawWrappedText(c, s(R.string.license_channels), body.left, y, body.width(), dp(15.5f));
            y += dp(12);

            text.setFakeBoldText(true);
            text.setColor(primaryText);
            c.drawText(s(R.string.license_not_allowed), body.left, y, text);
            text.setFakeBoldText(false);
            y += dp(19);

            String[] entries = a(R.array.license_restrictions);

            text.setTextSize(dp(10.7f));
            text.setColor(primaryText);
            float bulletX = body.left + dp(4);
            float textX = body.left + dp(18);
            float maxTextWidth = body.right - textX;
            for (String entry : entries) {
                y = drawWrappedBullet(c, entry, bulletX, textX, y, maxTextWidth, dp(14.2f));
                y += dp(1.5f);
            }

            y += dp(5);
            text.setTextSize(dp(9.6f));
            text.setColor(secondaryText);
            y = drawWrappedText(c,
                    s(R.string.license_kofi),
                    body.left,
                    y,
                    body.width(),
                    dp(13.2f));
            y += dp(5);
            y = drawWrappedText(c,
                    s(R.string.license_bzfe),
                    body.left,
                    y,
                    body.width(),
                    dp(13.2f));
            y += dp(5);
            y = drawWrappedText(c,
                    s(R.string.license_rights),
                    body.left,
                    y,
                    body.width(),
                    dp(13.2f));
            y += dp(5);
            drawWrappedText(c,
                    s(R.string.license_disclaimer),
                    body.left,
                    y,
                    body.width(),
                    dp(13.2f));
        }

        private void drawBackupOptionsOverlay(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 150 : 92));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, this::closeBackupOptions));

            float panelWidth = Math.min(w - dp(44), dp(420));
            float panelHeight = Math.min(dp(440), h - dp(64));
            float panelLeft = (w - panelWidth) / 2f;
            float panelTop = Math.max(dp(32), (h - panelHeight) / 2f);
            RectF panel = new RectF(
                    panelLeft,
                    panelTop,
                    panelLeft + panelWidth,
                    panelTop + panelHeight);
            drawCard(c, panel);

            RectF close = new RectF(
                    panel.right - dp(46),
                    panel.top + dp(10),
                    panel.right - dp(10),
                    panel.top + dp(46));
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(false);
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText("\u00d7", close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, this::closeBackupOptions));

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(21));
            text.setColor(accent);
            c.drawText(s(R.string.backup_title), panel.centerX(), panel.top + dp(48), text);
            text.setFakeBoldText(false);

            text.setTextSize(dp(11.5f));
            text.setColor(secondaryText);
            c.drawText(
                    s(R.string.settings_backup_description),
                    panel.centerX(),
                    panel.top + dp(70),
                    text);

            float side = dp(24);
            float rowTop = panel.top + dp(88);
            // Adaptive sizing: compact on constrained heights
            float rowHeight = panelHeight < dp(380) ? dp(50) : dp(58);
            float rowGap = panelHeight < dp(380) ? dp(8) : dp(10);
            float cancelHeight = panelHeight < dp(380) ? dp(40) : dp(44);

            RectF save = new RectF(
                    panel.left + side,
                    rowTop,
                    panel.right - side,
                    rowTop + rowHeight);
            drawDialogChoice(
                    c,
                    save,
                    s(R.string.backup_save),
                    s(R.string.backup_save_description));
            targets.add(new TouchTarget(save, () -> {
                closeBackupOptions();
                startExportFlow();
            }));

            RectF load = new RectF(
                    save.left,
                    save.bottom + rowGap,
                    save.right,
                    save.bottom + rowGap + rowHeight);
            drawDialogChoice(
                    c,
                    load,
                    s(R.string.backup_load),
                    s(R.string.backup_load_description));
            targets.add(new TouchTarget(load, () -> {
                closeBackupOptions();
                startImportFlow();
            }));

            RectF pdf = new RectF(
                    load.left,
                    load.bottom + rowGap,
                    load.right,
                    load.bottom + rowGap + rowHeight);
            drawDialogChoice(
                    c,
                    pdf,
                    s(R.string.backup_pdf),
                    s(R.string.backup_pdf_description));
            targets.add(new TouchTarget(pdf, () -> {
                closeBackupOptions();
                startPdfExportConfiguration();
            }));

            // Companion - always shown as a single row that opens a dedicated submenu
            RectF companion = new RectF(
                    pdf.left,
                    pdf.bottom + rowGap,
                    pdf.right,
                    pdf.bottom + rowGap + rowHeight);
            drawDialogChoice(c, companion, s(R.string.backup_companion), s(R.string.backup_companion_description));
            targets.add(new TouchTarget(companion, () -> {
                closeBackupOptions();
                showCompanionBackupMenu();
            }));

            RectF cancel = new RectF(
                    panel.left + dp(54),
                    companion.bottom + rowGap,
                    panel.right - dp(54),
                    companion.bottom + rowGap + cancelHeight);
            drawDialogButton(c, cancel, s(R.string.action_cancel), false);
            targets.add(new TouchTarget(cancel, this::closeBackupOptions));
        }

        private void drawCompanionBackupMenu(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 150 : 92));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, this::closeCompanionBackupMenu));

            float panelWidth = Math.min(w - dp(44), dp(360));
            float panelHeight = Math.min(dp(280), Math.max(dp(220), h - dp(64)));
            boolean compact = panelHeight < dp(260);

            float panelLeft = (w - panelWidth) / 2f;
            float panelTop = Math.max(dp(8), (h - panelHeight) / 2f);
            if (panelTop + panelHeight > h - dp(8)) {
                panelTop = Math.max(dp(8), h - dp(8) - panelHeight);
            }

            RectF panel = new RectF(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight);
            drawCard(c, panel);

            RectF close = new RectF(panel.right - dp(46), panel.top + dp(10), panel.right - dp(10), panel.top + dp(46));
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(false);
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText("×", close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, this::closeCompanionBackupMenu));

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(compact ? 18 : 20));
            text.setColor(accent);
            c.drawText(s(R.string.backup_companion), panel.centerX(), panel.top + dp(compact ? 34 : 48), text);
            text.setFakeBoldText(false);

            text.setTextSize(dp(11.5f));
            text.setColor(secondaryText);
            c.drawText(s(R.string.backup_companion_description), panel.centerX(), panel.top + dp(compact ? 52 : 70), text);

            float side = dp(36);
            float rowHeight = dp(compact ? 44 : 50);
            float rowGap = dp(compact ? 8 : 12);
            float cancelHeight = dp(compact ? 38 : 44);
            float bottomPad = dp(compact ? 14 : 18);

            RectF cancel = new RectF(
                    panel.left + dp(54),
                    panel.bottom - bottomPad - cancelHeight,
                    panel.right - dp(54),
                    panel.bottom - bottomPad);
            RectF importR = new RectF(
                    panel.left + side,
                    cancel.top - rowGap - rowHeight,
                    panel.right - side,
                    cancel.top - rowGap);
            RectF export = new RectF(
                    panel.left + side,
                    importR.top - rowGap - rowHeight,
                    panel.right - side,
                    importR.top - rowGap);

            drawDialogChoice(c, export, s(R.string.backup_companion_save), s(R.string.backup_companion_save_description));
            targets.add(new TouchTarget(export, () -> {
                closeCompanionBackupMenu();
                startExportCompanionBackupFlow();
            }));

            drawDialogChoice(c, importR, s(R.string.backup_companion_load), s(R.string.backup_companion_load_description));
            targets.add(new TouchTarget(importR, () -> {
                closeCompanionBackupMenu();
                startImportCompanionBackupFlow();
            }));

            drawDialogButton(c, cancel, s(R.string.action_cancel), false);
            targets.add(new TouchTarget(cancel, this::closeCompanionBackupMenu));
        }

        private void drawRestoreWarningOverlay(Canvas c, float w, float h) {
            RectF dim = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.BLACK, dark ? 165 : 105));
            c.drawRect(dim, p);
            targets.add(new TouchTarget(dim, this::closeRestoreWarning));

            float panelWidth = Math.min(w - dp(40), dp(430));
            float panelHeight = Math.min(dp(390), h - dp(88));
            float panelLeft = (w - panelWidth) / 2f;
            float panelTop = Math.max(dp(44), (h - panelHeight) / 2f);
            RectF panel = new RectF(
                    panelLeft,
                    panelTop,
                    panelLeft + panelWidth,
                    panelTop + panelHeight);
            drawCard(c, panel);

            RectF close = new RectF(
                    panel.right - dp(46),
                    panel.top + dp(10),
                    panel.right - dp(10),
                    panel.top + dp(46));
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(false);
            text.setTextSize(dp(24));
            text.setColor(secondaryText);
            c.drawText("\u00d7", close.centerX(), close.centerY() + dp(8), text);
            targets.add(new TouchTarget(close, this::closeRestoreWarning));

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(20));
            text.setColor(accent);
            c.drawText(
                    s(R.string.backup_restore_title),
                    panel.centerX(),
                    panel.top + dp(48),
                    text);
            text.setFakeBoldText(false);

            text.setTextSize(dp(11.5f));
            text.setColor(secondaryText);
            c.drawText(
                    s(R.string.backup_restore_subtitle),
                    panel.centerX(),
                    panel.top + dp(70),
                    text);

            RectF warning = new RectF(
                    panel.left + dp(24),
                    panel.top + dp(92),
                    panel.right - dp(24),
                    panel.top + dp(230));
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark
                    ? alpha(yellow, 34)
                    : alpha(yellow, 42));
            c.drawRoundRect(warning, dp(16), dp(16), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(dark
                    ? alpha(yellow, 185)
                    : darken(yellow, .78f));
            c.drawRoundRect(warning, dp(16), dp(16), p);

            text.setTextAlign(Paint.Align.LEFT);
            text.setFakeBoldText(false);
            text.setTextSize(dp(12));
            text.setColor(primaryText);
            drawWrappedText(
                    c,
                    s(R.string.backup_restore_warning),
                    warning.left + dp(18),
                    warning.top + dp(30),
                    warning.width() - dp(36),
                    dp(18));

            RectF confirm = new RectF(
                    panel.left + dp(28),
                    panel.bottom - dp(108),
                    panel.right - dp(28),
                    panel.bottom - dp(60));
            drawDialogButton(c, confirm, s(R.string.action_overwrite_load), true);
            targets.add(new TouchTarget(confirm, () -> {
                BackupData backup = pendingRestoreBackup;
                closeRestoreWarning();
                if (backup != null) {
                    applyBackup(backup);
                }
            }));

            RectF cancel = new RectF(
                    panel.left + dp(54),
                    panel.bottom - dp(50),
                    panel.right - dp(54),
                    panel.bottom - dp(12));
            drawDialogButton(c, cancel, s(R.string.action_cancel), false);
            targets.add(new TouchTarget(cancel, this::closeRestoreWarning));
        }

        private void drawDialogChoice(
                Canvas c,
                RectF r,
                String title,
                String description) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark
                    ? alpha(Color.WHITE, 18)
                    : alpha(Color.WHITE, 125));
            c.drawRoundRect(r, dp(15), dp(15), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(r, dp(15), dp(15), p);

            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(primaryText);
            drawFittedText(
                    c,
                    title,
                    r.left + dp(16),
                    r.centerY() - dp(3),
                    r.width() - dp(54),
                    dp(14),
                    dp(10.5f),
                    true);
            text.setColor(secondaryText);
            drawFittedText(
                    c,
                    description,
                    r.left + dp(16),
                    r.centerY() + dp(15),
                    r.width() - dp(54),
                    dp(10.5f),
                    dp(8.5f),
                    false);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(19));
            text.setColor(accent);
            c.drawText("\u203a", r.right - dp(22), r.centerY() + dp(6), text);
        }

        private void drawDialogButton(
                Canvas c,
                RectF r,
                String label,
                boolean primary) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(primary
                    ? accent
                    : (dark ? alpha(Color.WHITE, 16) : alpha(Color.WHITE, 100)));
            c.drawRoundRect(r, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(primary ? 1.2f : 1f));
            p.setColor(primary ? accent : outline);
            c.drawRoundRect(r, dp(14), dp(14), p);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(primary);
            text.setTextSize(dp(13));
            text.setColor(primary
                    ? (dark ? Color.rgb(31, 28, 33) : Color.WHITE)
                    : primaryText);
            c.drawText(label, r.centerX(), r.centerY() + dp(5), text);
            text.setFakeBoldText(false);
        }

        private void closeBackupOptions() {
            backupOptionsOpen = false;
            invalidate();
        }

        private void closeRestoreWarning() {
            restoreWarningOpen = false;
            pendingRestoreBackup = null;
            invalidate();
        }

        private float drawWrappedText(Canvas c, String entry, float x, float y, float maxWidth, float lineHeight) {
            List<String> lines = wrapTextLines(entry, maxWidth);
            float currentY = y;
            for (String line : lines) {
                c.drawText(line, x, currentY, text);
                currentY += lineHeight;
            }
            return currentY;
        }

        private String ellipsizeText(String value, float maxWidth) {
            if (value == null || value.isEmpty()) return "";
            if (text.measureText(value) <= maxWidth) return value;
            String ellipsis = "\u2026";
            for (int end = value.length() - 1; end > 0; end--) {
                String candidate = value.substring(0, end).trim() + ellipsis;
                if (text.measureText(candidate) <= maxWidth) return candidate;
            }
            return ellipsis;
        }

        private void drawKoFiCupIcon(Canvas c, RectF r) {
            float s = Math.min(r.width(), r.height());
            float cx = r.centerX();
            float cy = r.centerY();

            RectF cup = new RectF(cx - s * .37f, cy - s * .22f, cx + s * .18f, cy + s * .22f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawRoundRect(cup, dp(4), dp(4), p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.WHITE);
            RectF handle = new RectF(cup.right - s * .04f, cup.top + s * .05f, cup.right + s * .26f, cup.bottom - s * .05f);
            c.drawArc(handle, -75, 150, false, p);

            p.setStyle(Paint.Style.FILL);
            Path heart = new Path();
            heart.moveTo(cx - s * .16f, cy - s * .02f);
            heart.cubicTo(cx - s * .27f, cy - s * .14f, cx - s * .08f, cy - s * .25f, cx, cy - s * .11f);
            heart.cubicTo(cx + s * .08f, cy - s * .25f, cx + s * .27f, cy - s * .14f, cx + s * .16f, cy - s * .02f);
            heart.lineTo(cx, cy + s * .14f);
            heart.close();
            p.setColor(Color.rgb(239, 94, 98));
            c.drawPath(heart, p);

            p.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawSettingsSlidersIcon(Canvas c, RectF r, int col) {
            float left = r.left + r.width() * .24f;
            float right = r.right - r.width() * .24f;
            float y1 = r.top + r.height() * .33f;
            float y2 = r.top + r.height() * .50f;
            float y3 = r.top + r.height() * .67f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(2.1f));
            p.setColor(col);
            c.drawLine(left, y1, right, y1, p);
            c.drawLine(left, y2, right, y2, p);
            c.drawLine(left, y3, right, y3, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            c.drawCircle(left + (right - left) * .68f, y1, dp(3.2f), p);
            c.drawCircle(left + (right - left) * .34f, y2, dp(3.2f), p);
            c.drawCircle(left + (right - left) * .78f, y3, dp(3.2f), p);

            p.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawEditBadge(Canvas c, RectF r) {
            float s = Math.min(r.width(), r.height());
            float cx = r.centerX();
            float cy = r.centerY();

            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? alpha(accent, 220) : alpha(accent, 190));
            c.drawOval(r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(dark ? alpha(Color.WHITE, 150) : alpha(Color.WHITE, 180));
            c.drawOval(inset(r, dp(.5f), dp(.5f)), p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? Color.WHITE : Color.rgb(31, 28, 33));
            c.save();
            c.rotate(-42f, cx, cy);
            RectF body = new RectF(cx - s * .20f, cy - s * .075f, cx + s * .12f, cy + s * .075f);
            c.drawRoundRect(body, dp(1.2f), dp(1.2f), p);

            RectF eraser = new RectF(cx - s * .32f, cy - s * .07f, cx - s * .22f, cy + s * .07f);
            c.drawRoundRect(eraser, dp(1.1f), dp(1.1f), p);

            Path tip = new Path();
            tip.moveTo(cx + s * .12f, cy - s * .075f);
            tip.lineTo(cx + s * .26f, cy);
            tip.lineTo(cx + s * .12f, cy + s * .075f);
            tip.close();
            c.drawPath(tip, p);
            c.restore();
        }

        private void drawAboutBrand(Canvas c, RectF r) {
            if (aboutBrandBmp != null) {
                drawCircularBitmapIcon(c, aboutBrandBmp, r);
                return;
            }
            drawLambBeeAvatar(c, r);
        }

        private float drawWrappedBullet(Canvas c, String entry, float bulletX, float textX, float y, float maxWidth, float lineHeight) {
            c.drawText("\u2022", bulletX, y, text);
            List<String> lines = wrapTextLines(entry, maxWidth);
            float currentY = y;
            for (String line : lines) {
                c.drawText(line, textX, currentY, text);
                currentY += lineHeight;
            }
            return currentY;
        }

        private int wrappedLineCount(String entry, float maxWidth) {
            return Math.max(1, wrapTextLines(entry, maxWidth).size());
        }

        private List<String> wrapTextLines(String entry, float maxWidth) {
            List<String> lines = new ArrayList<>();
            if (entry == null || entry.isEmpty()) {
                lines.add("");
                return lines;
            }
            String[] words = entry.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (text.measureText(candidate) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        }

        private String appVersionLabel() {
            return s(R.string.about_version_format, BuildConfig.VERSION_NAME);
        }

        private static final class PortionCategoryMetrics {
            final float cardHeight;
            final float textSize;
            final float lineHeight;
            final float baselineOffset;
            final float rowGap;
            final float nameMaxWidth;
            final float measureMaxWidth;
            final float[] rowHeights;
            final int[] nameLineCounts;
            final int[] measureLineCounts;
            final float contentHeight;
            final int sharedMeasureLineCount;

            PortionCategoryMetrics(
                    float cardHeight,
                    float textSize,
                    float lineHeight,
                    float baselineOffset,
                    float rowGap,
                    float nameMaxWidth,
                    float measureMaxWidth,
                    float[] rowHeights,
                    int[] nameLineCounts,
                    int[] measureLineCounts,
                    float contentHeight,
                    int sharedMeasureLineCount) {
                this.cardHeight = cardHeight;
                this.textSize = textSize;
                this.lineHeight = lineHeight;
                this.baselineOffset = baselineOffset;
                this.rowGap = rowGap;
                this.nameMaxWidth = nameMaxWidth;
                this.measureMaxWidth = measureMaxWidth;
                this.rowHeights = rowHeights;
                this.nameLineCounts = nameLineCounts;
                this.measureLineCounts = measureLineCounts;
                this.contentHeight = contentHeight;
                this.sharedMeasureLineCount = sharedMeasureLineCount;
            }
        }

        private void drawCard(Canvas c, RectF r) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(card);
            c.drawRoundRect(r, dp(24), dp(24), p);
            if (isPastelCozy()) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2.1f));
                p.setColor(alpha(Color.WHITE, dark ? 22 : 115));
                c.drawRoundRect(inset(r, dp(2), dp(2)), dp(22), dp(22), p);
                p.setStrokeWidth(dp(1));
                p.setColor(outline);
                c.drawRoundRect(r, dp(24), dp(24), p);
            }
        }

        private void openSupportLink() {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s(R.string.support_url)));
                getContext().startActivity(intent);
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_support));
            }
        }

        private void openBzfeSourceLink() {
            try {
                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(s(R.string.bzfe_url)));
                getContext().startActivity(intent);
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_bzfe_unavailable));
            }
        }

        private void showBackupOptions() {
            closeSettingsOverlay();
            backupOptionsOpen = true;
            restoreWarningOpen = false;
            pendingRestoreBackup = null;
            invalidate();
        }

        private void showCompanionBackupMenu() {
            companionBackupMenuOpen = true;
            invalidate();
        }

        private void closeCompanionBackupMenu() {
            companionBackupMenuOpen = false;
            invalidate();
        }

        private void startPdfExportConfiguration() {
            Context context = getContext();
            if (!(context instanceof MainActivity)) {
                showSnackbar(s(R.string.snackbar_pdf_unavailable));
                postInvalidateOnAnimation();
                return;
            }

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = firstUsed(endDate);
            try {
                updatePalette();
                PyramidPdfExportDialog.show(
                        (MainActivity) context,
                        pdfDialogTheme(),
                        new PyramidPdfExportDialog.Palette(
                                bg,
                                card,
                                primaryText,
                                secondaryText,
                                accent,
                                outline,
                                red,
                                dark),
                        startDate,
                        endDate,
                        this::startPdfDocumentFlow);
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_pdf_unavailable));
                postInvalidateOnAnimation();
            }
        }

        private int pdfDialogTheme() {
            if (isPastelCozy()) {
                return dark
                        ? R.style.EscaDialogPastelDark
                        : R.style.EscaDialogPastelLight;
            }
            return dark
                    ? R.style.EscaDialogStandardDark
                    : R.style.EscaDialogStandardLight;
        }

        private void startPdfDocumentFlow(PyramidPdfExportDialog.Config config) {
            Context context = getContext();
            if (!(context instanceof MainActivity) || config == null) {
                showSnackbar(s(R.string.snackbar_pdf_unavailable));
                postInvalidateOnAnimation();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    s(
                            R.string.pdf_file_name_format,
                            config.startDate.format(KEY_FORMAT),
                            config.endDate.format(KEY_FORMAT)));

            try {
                MainActivity activity = (MainActivity) context;
                activity.pendingPdfExportConfig = config;
                activity.startActivityForResult(intent, REQUEST_EXPORT_PDF);
                closeSettingsOverlay();
                invalidate();
            } catch (Exception ignored) {
                ((MainActivity) context).pendingPdfExportConfig = null;
                showSnackbar(s(R.string.snackbar_picker_unavailable));
                postInvalidateOnAnimation();
            }
        }

        private void startExportFlow() {
            Context context = getContext();
            if (!(context instanceof Activity)) {
                showSnackbar(s(R.string.snackbar_backup_unavailable));
                postInvalidateOnAnimation();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, defaultBackupFileName());

            try {
                ((Activity) context).startActivityForResult(intent, REQUEST_EXPORT_JSON);
                closeSettingsOverlay();
                invalidate();
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_picker_unavailable));
                postInvalidateOnAnimation();
            }
        }

        private void startImportFlow() {
            Context context = getContext();
            if (!(context instanceof Activity)) {
                showSnackbar(s(R.string.snackbar_load_unavailable));
                postInvalidateOnAnimation();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "application/json", "text/json", "text/plain"
            });

            try {
                ((Activity) context).startActivityForResult(intent, REQUEST_IMPORT_JSON);
                closeSettingsOverlay();
                invalidate();
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_picker_unavailable));
                postInvalidateOnAnimation();
            }
        }

        private void startExportCompanionBackupFlow() {
            Context context = getContext();
            if (!(context instanceof Activity)) {
                showSnackbar(s(R.string.snackbar_companion_backup_failed));
                postInvalidateOnAnimation();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, defaultCompanionBackupFileName());

            try {
                ((Activity) context).startActivityForResult(intent, REQUEST_EXPORT_COMPANION_BACKUP);
                closeSettingsOverlay();
                invalidate();
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_picker_unavailable));
                postInvalidateOnAnimation();
            }
        }

        private void startImportCompanionBackupFlow() {
            Context context = getContext();
            if (!(context instanceof Activity)) {
                showSnackbar(s(R.string.snackbar_companion_backup_load_failed));
                postInvalidateOnAnimation();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "application/json", "text/json", "text/plain"
            });

            try {
                ((Activity) context).startActivityForResult(intent, REQUEST_IMPORT_COMPANION_BACKUP);
                closeSettingsOverlay();
                invalidate();
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_picker_unavailable));
                postInvalidateOnAnimation();
            }
        }

        private void handlePdfDocumentResult(
                Uri uri,
                PyramidPdfExportDialog.Config config) {
            if (uri == null) {
                showSnackbar(s(R.string.snackbar_pdf_cancelled));
                postInvalidateOnAnimation();
                return;
            }
            if (config == null) {
                showSnackbar(s(R.string.snackbar_pdf_failed));
                postInvalidateOnAnimation();
                return;
            }

            try {
                Map<LocalDate, PyramidScheme.DayState> recordedDays =
                        PyramidReportSource.fromPreferences(
                                prefs.getAll(),
                                config.startDate,
                                config.endDate);
                PyramidReportModel.Report report = PyramidReportModel.build(
                        recordedDays,
                        config.startDate,
                        config.endDate,
                        config.sections);

                OutputStream outputStream =
                        getContext().getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    throw new IllegalStateException("No PDF output stream");
                }
                try (OutputStream output = outputStream) {
                    PyramidPdfWriter.write(output, report, pdfLabels());
                }
                showSnackbar(s(R.string.snackbar_pdf_saved));
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_pdf_failed));
            }
            postInvalidateOnAnimation();
        }

        private PyramidPdfWriter.Labels pdfLabels() {
            return new PyramidPdfWriter.Labels(
                    s(R.string.report_title),
                    s(R.string.report_period),
                    s(R.string.report_green),
                    s(R.string.report_yellow),
                    s(R.string.report_red),
                    s(R.string.report_section_detailed),
                    s(R.string.report_section_weekly),
                    s(R.string.report_section_monthly),
                    s(R.string.report_section_total),
                    s(R.string.report_app_version_format, BuildConfig.VERSION_NAME),
                    s(
                            R.string.report_generated_on_format,
                            PyramidReportFormat.day(LocalDate.now())),
                    s(R.string.report_pyramid_definition),
                    s(R.string.license_bzfe),
                    s(R.string.report_notice),
                    s(R.string.report_page_format));
        }

        private void handleExportDocumentResult(Uri uri) {
            if (uri == null) {
                showSnackbar(s(R.string.snackbar_backup_cancelled));
                postInvalidateOnAnimation();
                return;
            }

            try {
                writeExportJson(uri);
                showSnackbar(s(R.string.snackbar_backup_saved));
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_backup_failed));
            }
            postInvalidateOnAnimation();
        }

        private void handleImportDocumentResult(Uri uri) {
            if (uri == null) {
                showSnackbar(s(R.string.snackbar_load_cancelled));
                postInvalidateOnAnimation();
                return;
            }

            try {
                BackupData backup = readAndValidateBackup(uri);
                showRestoreWarning(backup);
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_backup_invalid));
                postInvalidateOnAnimation();
            }
        }

        private void handleExportCompanionBackupResult(Uri uri) {
            if (uri == null) {
                showSnackbar(s(R.string.snackbar_companion_backup_cancelled));
                postInvalidateOnAnimation();
                return;
            }

            try {
                writeExportCompanionBackupJson(uri);
                showSnackbar(s(R.string.snackbar_companion_backup_saved));
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_companion_backup_failed));
            }
            postInvalidateOnAnimation();
        }

        private void handleImportCompanionBackupResult(Uri uri) {
            if (uri == null) {
                showSnackbar(s(R.string.snackbar_companion_backup_cancelled));
                postInvalidateOnAnimation();
                return;
            }

            try {
                readAndValidateCompanionBackup(uri);
                showSnackbar(s(R.string.snackbar_companion_backup_loaded));
            } catch (Exception ignored) {
                showSnackbar(s(R.string.snackbar_companion_backup_invalid));
            }
            postInvalidateOnAnimation();
        }

        private void showRestoreWarning(BackupData backup) {
            pendingRestoreBackup = backup;
            restoreWarningOpen = true;
            backupOptionsOpen = false;
            invalidate();
        }

        private void writeExportJson(Uri uri) throws Exception {
            OutputStream outputStream = getContext().getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                throw new IllegalStateException("No output stream");
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write(buildExportJson().toString(2));
                writer.write("\n");
            }
        }

        private void writeExportCompanionBackupJson(Uri uri) throws Exception {
            OutputStream outputStream = getContext().getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                throw new IllegalStateException("No output stream");
            }
            long nowEpochMillis = System.currentTimeMillis();
            CompanionBackupManager.ExportResult exportResult = companionBackupManager.exportBackup(nowEpochMillis);
            if (exportResult.status != CompanionBackupManager.ExportStatus.EXPORTED || exportResult.document == null) {
                throw new IllegalStateException("Failed to export companion backup");
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                JSONObject json = new JSONObject(exportResult.document);
                writer.write(json.toString(2));
                writer.write("\n");
            }
        }

        private void readAndValidateCompanionBackup(Uri uri) throws Exception {
            String json = readJsonDocument(uri);
            JSONObject jsonObject = new JSONObject(json);

            @SuppressWarnings("unchecked")
            Map<String, Object> document = jsonToMap(jsonObject);

            long nowEpochMillis = System.currentTimeMillis();
            CompanionBackupManager.RestoreResult restoreResult = companionBackupManager.restoreBackup(document, nowEpochMillis);
            if (!restoreResult.status.equals(CompanionBackupManager.RestoreStatus.APPLIED) &&
                !restoreResult.status.equals(CompanionBackupManager.RestoreStatus.NO_CHANGE)) {
                throw new IllegalStateException("Companion backup restore failed: " + restoreResult.status);
            }
            companionUiState = loadCompanionUiState();
            invalidate();
        }

        private Map<String, Object> jsonToMap(JSONObject obj) throws JSONException {
            Map<String, Object> out = new LinkedHashMap<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object v = obj.get(key);
                if (v instanceof JSONObject) {
                    out.put(key, jsonToMap((JSONObject) v));
                } else if (v instanceof JSONArray) {
                    out.put(key, jsonArrayToList((JSONArray) v));
                } else if (JSONObject.NULL.equals(v)) {
                    out.put(key, null);
                } else {
                    out.put(key, v);
                }
            }
            return out;
        }

        private List<Object> jsonArrayToList(JSONArray arr) throws JSONException {
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.get(i);
                if (v instanceof JSONObject) {
                    out.add(jsonToMap((JSONObject) v));
                } else if (v instanceof JSONArray) {
                    out.add(jsonArrayToList((JSONArray) v));
                } else if (JSONObject.NULL.equals(v)) {
                    out.add(null);
                } else {
                    out.add(v);
                }
            }
            return out;
        }

        private JSONObject buildExportJson() throws JSONException {
            Map<String, ?> allPrefs = prefs.getAll();

            JSONObject root = new JSONObject();
            root.put("schemaVersion", PyramidScheme.BACKUP_SCHEMA_VERSION);
            root.put("schemaName", "esca-agnellis-export");
            root.put("exportedAt", Instant.now().toString());

            JSONObject app = new JSONObject();
            app.put("name", "Esca Agnellis");
            app.put("packageName", BuildConfig.APPLICATION_ID);
            app.put("versionName", BuildConfig.VERSION_NAME);
            app.put("versionCode", BuildConfig.VERSION_CODE);
            app.put("debugBuild", BuildConfig.DEBUG);
            root.put("app", app);

            root.put("tracking", buildTrackingJson(allPrefs));
            root.put("settings", buildSettingsJson());
            return root;
        }

        private JSONObject buildTrackingJson(Map<String, ?> allPrefs) throws JSONException {
            JSONObject tracking = new JSONObject();
            String firstUsedValue = prefs.getString("first_used", null);
            tracking.put("firstUsed", firstUsedValue == null ? JSONObject.NULL : firstUsedValue);
            tracking.put("baseTileCount", BASE_TILE_COUNT);
            tracking.put("subtypeCount", PyramidScheme.SUBTYPE_COUNT);

            JSONArray groupIdentifiers = new JSONArray();
            for (String groupId : GROUP_IDS) groupIdentifiers.put(groupId);
            tracking.put("groupIdentifiers", groupIdentifiers);

            List<String> dayKeys = new ArrayList<>();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith("day_") && key.length() > 4) {
                    dayKeys.add(key);
                }
            }
            Collections.sort(dayKeys);

            JSONArray days = new JSONArray();
            for (String key : dayKeys) {
                Object value = allPrefs.get(key);
                if (!(value instanceof String)) {
                    throw new JSONException("Invalid stored day value");
                }
                String date = key.substring(4);
                LocalDate parsedDate = LocalDate.parse(date, KEY_FORMAT);
                if (!date.equals(parsedDate.format(KEY_FORMAT))) {
                    throw new JSONException("Invalid stored day date");
                }

                DayData dayData = parseDayValue((String) value);
                JSONObject day = new JSONObject();
                day.put("date", date);
                day.put("ticks", booleanArrayJson(dayData.ticks));
                day.put("extrasBySubtype", intArrayJson(dayData.subtypeExtras));
                days.put(day);
            }
            tracking.put("days", days);
            return tracking;
        }

        private JSONObject buildSettingsJson() throws JSONException {
            JSONObject settings = new JSONObject();
            settings.put("themeMode", themeMode);
            settings.put("themeModeName", themeModeName(themeMode));
            settings.put("skinStyle", skinStyle);
            settings.put("skinStyleName", skinStyleName(skinStyle));
            settings.put("selectedDate", selectedDate.format(KEY_FORMAT));
            settings.put("preferences", new JSONObject());
            return settings;
        }

        private BackupData readAndValidateBackup(Uri uri) throws Exception {
            String json = readJsonDocument(uri);
            JSONObject root = new JSONObject(json);

            int schemaVersion = requireInt(
                    root,
                    "schemaVersion",
                    PyramidScheme.BACKUP_SCHEMA_VERSION,
                    PyramidScheme.BACKUP_SCHEMA_VERSION);
            try {
                PyramidScheme.requireCurrentBackupSchema(schemaVersion);
            } catch (IllegalArgumentException ex) {
                throw new JSONException("Unsupported schema version");
            }
            if (!"esca-agnellis-export".equals(requireString(root, "schemaName"))) {
                throw new JSONException("Unsupported schema name");
            }

            JSONObject app = root.optJSONObject("app");
            if (app != null && app.has("packageName")
                    && !BuildConfig.APPLICATION_ID.equals(requireString(app, "packageName"))) {
                throw new JSONException("Backup belongs to another app");
            }

            JSONObject tracking = root.getJSONObject("tracking");
            JSONObject settings = root.getJSONObject("settings");

            if (requireInt(tracking, "baseTileCount", BASE_TILE_COUNT, BASE_TILE_COUNT)
                    != BASE_TILE_COUNT
                    || requireInt(
                            tracking,
                            "subtypeCount",
                            PyramidScheme.SUBTYPE_COUNT,
                            PyramidScheme.SUBTYPE_COUNT)
                    != PyramidScheme.SUBTYPE_COUNT) {
                throw new JSONException("Incompatible tracking dimensions");
            }

            JSONArray identifiers = tracking.optJSONArray("groupIdentifiers");
            if (identifiers == null || identifiers.length() != GROUP_IDS.length) {
                throw new JSONException("Invalid group identifiers");
            }
            for (int i = 0; i < GROUP_IDS.length; i++) {
                if (!GROUP_IDS[i].equals(identifiers.get(i))) {
                    throw new JSONException("Invalid group identifier");
                }
            }

            String firstUsed = null;
            if (!tracking.isNull("firstUsed")) {
                firstUsed = requireString(tracking, "firstUsed");
                LocalDate parsedFirstUsed = LocalDate.parse(firstUsed, KEY_FORMAT);
                if (!firstUsed.equals(parsedFirstUsed.format(KEY_FORMAT))) {
                    throw new JSONException("Invalid first-used date");
                }
            }

            Map<String, String> serializedDays = new LinkedHashMap<>();
            Set<String> seenDates = new HashSet<>();
            JSONArray days = tracking.getJSONArray("days");
            if (days.length() > 10000) {
                throw new JSONException("Too many days");
            }

            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);
                String date = requireString(day, "date");
                LocalDate parsedDate = LocalDate.parse(date, KEY_FORMAT);
                if (!date.equals(parsedDate.format(KEY_FORMAT)) || !seenDates.add(date)) {
                    throw new JSONException("Invalid or duplicate date");
                }

                boolean[] importedTicks = requireBooleanArray(day.getJSONArray("ticks"), BASE_TILE_COUNT);
                int[] importedExtras = requireIntArray(
                        day.getJSONArray("extrasBySubtype"),
                        PyramidScheme.SUBTYPE_COUNT);

                try {
                    PyramidScheme.DayState currentDay =
                            PyramidScheme.fromBackupArrays(importedTicks, importedExtras);
                    serializedDays.put(date, PyramidScheme.serialize(currentDay));
                } catch (IllegalArgumentException ex) {
                    throw new JSONException("Invalid pyramid data: " + ex.getMessage());
                }
            }

            String importedSelectedDate = requireString(settings, "selectedDate");
            PyramidScheme.BackupModel preparedBackup;
            try {
                preparedBackup = PyramidScheme.prepareBackupModel(
                        schemaVersion,
                        serializedDays,
                        importedSelectedDate);
            } catch (IllegalArgumentException ex) {
                throw new JSONException("Invalid backup data: " + ex.getMessage());
            }

            int importedThemeMode = requireInt(settings, "themeMode", MODE_SYSTEM, MODE_DARK);
            int importedSkinStyle = requireInt(settings, "skinStyle", SKIN_STANDARD, SKIN_PASTEL_COZY);

            JSONObject preferences = settings.getJSONObject("preferences");
            Set<String> preferenceKeys = new HashSet<>();
            Iterator<String> keys = preferences.keys();
            while (keys.hasNext()) {
                preferenceKeys.add(keys.next());
            }
            try {
                PyramidScheme.requireCurrentGenericBackupPreferenceKeys(preferenceKeys);
            } catch (IllegalArgumentException ex) {
                throw new JSONException(ex.getMessage());
            }

            Map<String, String> dayValues = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : preparedBackup.dayValues.entrySet()) {
                dayValues.put("day_" + entry.getKey(), entry.getValue());
            }
            return new BackupData(
                    firstUsed,
                    dayValues,
                    LocalDate.parse(preparedBackup.selectedDate, KEY_FORMAT),
                    importedThemeMode,
                    importedSkinStyle);
        }

        private String readJsonDocument(Uri uri) throws Exception {
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) throw new IllegalStateException("No input stream");

            StringBuilder result = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                while ((read = reader.read(buffer)) != -1) {
                    result.append(buffer, 0, read);
                    if (result.length() > 5_000_000) {
                        throw new IllegalStateException("Backup file too large");
                    }
                }
            }
            return result.toString();
        }

        private String requireString(JSONObject object, String key) throws JSONException {
            Object value = object.get(key);
            if (!(value instanceof String) || ((String) value).length() == 0) {
                throw new JSONException("Expected string: " + key);
            }
            return (String) value;
        }

        private int requireInt(JSONObject object, String key, int min, int max) throws JSONException {
            Object value = object.get(key);
            if (!(value instanceof Number)) throw new JSONException("Expected number: " + key);
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number)
                    || number < min || number > max) {
                throw new JSONException("Invalid integer: " + key);
            }
            return (int) number;
        }

        private boolean[] requireBooleanArray(JSONArray array, int expectedLength) throws JSONException {
            if (array.length() != expectedLength) throw new JSONException("Invalid boolean array length");
            boolean[] values = new boolean[expectedLength];
            for (int i = 0; i < expectedLength; i++) {
                Object value = array.get(i);
                if (!(value instanceof Boolean)) throw new JSONException("Invalid boolean array value");
                values[i] = (Boolean) value;
            }
            return values;
        }

        private int[] requireIntArray(JSONArray array, int expectedLength) throws JSONException {
            if (array.length() != expectedLength) throw new JSONException("Invalid integer array length");
            int[] values = new int[expectedLength];
            for (int i = 0; i < expectedLength; i++) {
                Object value = array.get(i);
                if (!(value instanceof Number)) throw new JSONException("Invalid integer array value");
                double number = ((Number) value).doubleValue();
                if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number)
                        || number < 0 || number > 1000000) {
                    throw new JSONException("Invalid integer array value");
                }
                values[i] = (int) number;
            }
            return values;
        }

        private void applyBackup(BackupData backup) {
            int acknowledgedNoticeVersion = prefs.getInt(FIRST_LAUNCH_ACK_VERSION_KEY, 0);
            Map<String, Object> originalPreferences =
                    PyramidScheme.copyPreferenceSnapshot(prefs.getAll());
            Map<String, Object> targetPreferences = new LinkedHashMap<>();
            if (backup.firstUsed != null) {
                targetPreferences.put("first_used", backup.firstUsed);
            }
            targetPreferences.putAll(backup.dayValues);
            targetPreferences.put("theme_mode", backup.themeMode);
            targetPreferences.put("skin_style", backup.skinStyle);
            targetPreferences.put(
                    PyramidScheme.SELECTED_DATE_KEY,
                    backup.selectedDate.format(KEY_FORMAT));
            if (acknowledgedNoticeVersion > 0) {
                targetPreferences.put(
                        FIRST_LAUNCH_ACK_VERSION_KEY,
                        acknowledgedNoticeVersion);
            }

            PyramidScheme.PreferenceTransactionResult result =
                    applyVerifiedPreferenceTransaction(
                            originalPreferences,
                            targetPreferences);
            if (result.outcome == PyramidScheme.TransactionOutcome.ORIGINAL_RESTORED) {
                themeMode = prefs.getInt("theme_mode", MODE_SYSTEM);
                skinStyle = prefs.getInt("skin_style", SKIN_PASTEL_COZY);
                loadDay(selectedDate);
                showSnackbar(s(R.string.snackbar_backup_load_failed));
                postInvalidateOnAnimation();
                return;
            }
            if (result.outcome == PyramidScheme.TransactionOutcome.INDETERMINATE) {
                setIndeterminateDataError();
                pendingRestoreBackup = null;
                restoreWarningOpen = false;
                backupOptionsOpen = false;
                invalidate();
                return;
            }

            themeMode = backup.themeMode;
            skinStyle = backup.skinStyle;
            selectedDate = backup.selectedDate;
            overviewMonth = selectedDate.withDayOfMonth(1);
            portionsScrollY = 0f;
            loadDay(selectedDate);
            showSnackbar(s(R.string.snackbar_backup_loaded));
            invalidate();
        }

        private PyramidScheme.PreferenceTransactionResult applyVerifiedPreferenceTransaction(
                Map<String, ?> originalSnapshot,
                Map<String, ?> targetSnapshot) {
            Map<String, Object> original =
                    PyramidScheme.copyPreferenceSnapshot(originalSnapshot);
            Map<String, Object> target =
                    PyramidScheme.copyPreferenceSnapshot(targetSnapshot);

            boolean targetCommitReported = commitPreferenceSnapshot(target);
            Map<String, Object> observedAfterTarget =
                    PyramidScheme.copyPreferenceSnapshot(prefs.getAll());
            if (PyramidScheme.preferenceSnapshotsEqual(target, observedAfterTarget)) {
                return PyramidScheme.classifyPreferenceTransaction(
                        targetCommitReported,
                        target,
                        observedAfterTarget,
                        false,
                        false,
                        original,
                        null);
            }

            boolean originalCommitReported = commitPreferenceSnapshot(original);
            Map<String, Object> observedAfterRollback =
                    PyramidScheme.copyPreferenceSnapshot(prefs.getAll());
            return PyramidScheme.classifyPreferenceTransaction(
                    targetCommitReported,
                    target,
                    observedAfterTarget,
                    true,
                    originalCommitReported,
                    original,
                    observedAfterRollback);
        }

        private boolean commitPreferenceSnapshot(Map<String, ?> snapshot) {
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : snapshot.entrySet()) {
                putImportedPreference(editor, entry.getKey(), entry.getValue());
            }
            return editor.commit();
        }

        @SuppressWarnings("unchecked")
        private void putImportedPreference(SharedPreferences.Editor editor, String key, Object value) {
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Set<?>) {
                editor.putStringSet(key, new HashSet<>((Set<String>) value));
            }
        }

        private JSONArray booleanArrayJson(boolean[] values) {
            JSONArray array = new JSONArray();
            for (boolean value : values) array.put(value);
            return array;
        }

        private JSONArray intArrayJson(int[] values) {
            JSONArray array = new JSONArray();
            for (int value : values) array.put(value);
            return array;
        }

        private String themeModeName(int mode) {
            switch (mode) {
                case MODE_LIGHT: return "light";
                case MODE_DARK: return "dark";
                default: return "system";
            }
        }

        private String skinStyleName(int style) {
            switch (style) {
                case SKIN_STANDARD: return "standard";
                case SKIN_PASTEL_COZY: return "pastel_cozy";
                default: return "unknown";
            }
        }

        private String defaultBackupFileName() {
            return s(R.string.backup_file_name_format, LocalDate.now().format(KEY_FORMAT));
        }

        private String defaultCompanionBackupFileName() {
            return s(R.string.backup_companion_file_name_format, LocalDate.now().format(KEY_FORMAT));
        }

        private void drawMigrationError(Canvas c, float w, float h) {
            float margin = dp(24);
            RectF panel = new RectF(
                    margin,
                    Math.max(dp(70), h / 2f - dp(130)),
                    w - margin,
                    Math.min(h - dp(70), h / 2f + dp(130)));
            drawCard(c, panel);

            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(dp(20));
            text.setColor(accent);
            c.drawText(blockingDataErrorTitle, panel.centerX(), panel.top + dp(42), text);
            text.setFakeBoldText(false);

            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(dp(12));
            text.setColor(primaryText);
            float y = drawWrappedText(
                    c,
                    blockingDataErrorMessage,
                    panel.left + dp(22),
                    panel.top + dp(78),
                    panel.width() - dp(44),
                    dp(17));
            y += dp(12);
            text.setColor(secondaryText);
            drawWrappedText(
                    c,
                    s(R.string.data_error_no_reset),
                    panel.left + dp(22),
                    y,
                    panel.width() - dp(44),
                    dp(16));
        }

        private void setBlockingDataError(String title, String message) {
            blockingDataErrorTitle = title;
            blockingDataErrorMessage = message;
        }

        private void setIndeterminateDataError() {
            setBlockingDataError(
                    s(R.string.data_error_title),
                    s(R.string.data_error_message));
        }

        private void drawSnackbar(Canvas c, float w, float h) {
            if (!isSnackbarVisible()) return;
            float bottom = h - dp(14);
            float top = companionPageState.isOpen() ? h - dp(64) : h - dp(142);
            float regularBottom = companionPageState.isOpen() ? bottom : h - dp(92);
            RectF r = new RectF(dp(22), top, w - dp(22), regularBottom);
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? Color.rgb(38, 47, 39) : Color.rgb(57, 92, 61));
            c.drawRoundRect(r, dp(16), dp(16), p);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(false);
            text.setTextSize(dp(13));
            text.setColor(Color.WHITE);
            c.drawText(snackbarText, r.centerX(), r.centerY() + dp(5), text);
            postInvalidateOnAnimation();
        }

        private boolean isSnackbarVisible() {
            return snackbarText != null && System.currentTimeMillis() <= snackbarUntil;
        }

        private void toggleTile(int index) {
            handleDefaultTileTap(index);
        }

        private void handleDefaultTileTap(int index) {
            boolean[] ticksBeforeTap = ticks.clone();
            int subtype = PyramidScheme.subtypeForPosition(index);
            boolean wasComplete = PyramidScheme.isSubtypeComplete(ticks, subtype);
            if (ticks[index]) {
                PyramidScheme.removeSubtype(ticks, subtypeExtras, subtype);
            } else {
                PyramidScheme.fillSubtype(ticks, subtype);
            }

            saveDay(selectedDate);
            long rewardEventTime = System.currentTimeMillis();
            boolean companionStateChanged = revokeCorrectedCompanionRewards(
                    selectedDate,
                    ticksBeforeTap,
                    ticks,
                    rewardEventTime);
            CompanionTrackingRewards.Summary companionRewards =
                    CompanionTrackingRewards.awardNewlySelectedDefaults(
                            selectedDate,
                            ticksBeforeTap,
                            ticks,
                            (date, position) -> rewardCompanionDefaultPosition(
                                    date,
                                    position,
                                    rewardEventTime));
            if (companionStateChanged || companionRewards.grantedCount > 0) {
                companionUiState = loadCompanionUiState();
            }
            if (!wasComplete
                    && PyramidScheme.isSubtypeComplete(ticks, subtype)
                    && CATEGORY_BY_SUBTYPE[subtype] == CAT_GREEN) {
                showSnackbar(s(R.string.snackbar_green_complete));
            }
        }

        private CompanionRepository.MutationResult rewardCompanionDefaultPosition(
                LocalDate date,
                int position,
                long grantedAtEpochMillis) {
            return companionRepository.rewardDefaultPosition(
                    date,
                    position,
                    grantedAtEpochMillis);
        }

        private boolean revokeCorrectedCompanionRewards(
                LocalDate date,
                boolean[] beforeTicks,
                boolean[] afterTicks,
                long correctedAtEpochMillis) {
            boolean changed = false;
            for (int position = 0; position < BASE_TILE_COUNT; position++) {
                if (!beforeTicks[position] || afterTicks[position]) {
                    continue;
                }

                CompanionRepository.MutationResult result =
                        companionRepository.correctDefaultPosition(
                                date,
                                position,
                                correctedAtEpochMillis);
                if (result != null
                        && result.status == CompanionRepository.MutationStatus.APPLIED
                        && result.economyOutcome == CompanionEconomy.Outcome.REWARD_REVOKED) {
                    changed = true;
                }
            }
            return changed;
        }

        private void addSubtypeExtra(int subtype) {
            if (!PyramidScheme.isSubtypeComplete(ticks, subtype)
                    || subtypeExtras[subtype] >= PyramidScheme.MAX_EXTRA_COUNT) {
                return;
            }
            subtypeExtras[subtype]++;
            saveDay(selectedDate);
            showSnackbar(s(R.string.snackbar_extra_added));
        }

        private void removeSubtypeExtra(int subtype) {
            PyramidScheme.removeSubtype(ticks, subtypeExtras, subtype);
            saveDay(selectedDate);
            showSnackbar(s(R.string.snackbar_extra_removed));
        }

        private void changeSelectedDate(LocalDate day) {
            selectedDate = day;
            overviewMonth = selectedDate.withDayOfMonth(1);
            prefs.edit()
                    .putString(PyramidScheme.SELECTED_DATE_KEY, day.format(KEY_FORMAT))
                    .apply();
            loadDay(selectedDate);
        }

        private void openOverviewDate(LocalDate day) {
            PyramidScheme.OverviewSelection selection =
                    PyramidScheme.selectOverviewDate(day, TAB_TODAY);
            selectedDate = selection.selectedDate;
            overviewMonth = selection.overviewMonth;
            activeTab = selection.activeTab;
            prefs.edit()
                    .putString(
                            PyramidScheme.SELECTED_DATE_KEY,
                            selectedDate.format(KEY_FORMAT))
                    .apply();
            loadDay(selectedDate);
            invalidate();
        }

        private LocalDate readSelectedDate() {
            String stored = prefs.getString(PyramidScheme.SELECTED_DATE_KEY, null);
            if (stored == null) return LocalDate.now();
            try {
                LocalDate parsed = LocalDate.parse(stored, KEY_FORMAT);
                return stored.equals(parsed.format(KEY_FORMAT)) ? parsed : LocalDate.now();
            } catch (Exception ignored) {
                return LocalDate.now();
            }
        }

        private void loadDay(LocalDate day) {
            DayData d = readDay(day);
            ticks = d.ticks;
            subtypeExtras = d.subtypeExtras;
        }

        private void saveDay(LocalDate day) {
            prefs.edit()
                    .putString(dayKey(day), serializeDayValue(ticks, subtypeExtras))
                    .putString("first_used", firstUsed(day).format(KEY_FORMAT))
                    .putString(
                            PyramidScheme.SELECTED_DATE_KEY,
                            selectedDate.format(KEY_FORMAT))
                    .apply();
        }

        private String serializeDayValue(boolean[] dayTicks, int[] daySubtypeExtras) {
            return PyramidScheme.serialize(new PyramidScheme.DayState(
                    dayTicks,
                    daySubtypeExtras));
        }

        private LocalDate firstUsed(LocalDate fallback) {
            String value = prefs.getString("first_used", null);
            if (value == null) return fallback;
            try {
                LocalDate stored = LocalDate.parse(value, KEY_FORMAT);
                return stored.isBefore(fallback) ? stored : fallback;
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private DayData readDay(LocalDate day) {
            String s = prefs.getString(dayKey(day), null);
            return parseDayValue(s);
        }

        private DayData parseDayValue(String s) {
            PyramidScheme.DayState parsed = PyramidScheme.parseStoredDay(s);
            return new DayData(
                    parsed.ticks,
                    parsed.subtypeExtras);
        }

        private String dayKey(LocalDate day) {
            return "day_" + day.format(KEY_FORMAT);
        }

        private PeriodStats calculatePeriod(int period) {
            LocalDate today = LocalDate.now();
            LocalDate start;
            LocalDate end;
            if (period == 0) {
                start = today.with(DayOfWeek.MONDAY);
                end = start.plusDays(6);
            } else if (period == 1) {
                YearMonth ym = YearMonth.from(today);
                start = ym.atDay(1);
                end = ym.atEndOfMonth();
            } else {
                start = firstUsed(today);
                end = today;
            }
            PeriodStats ps = new PeriodStats();
            LocalDate d = start;
            while (!d.isAfter(end)) {
                DayData dayData = readDay(d);
                boolean countUsed = !d.isAfter(today);
                addDayToStats(ps, dayData, countUsed);
                d = d.plusDays(1);
            }
            return ps;
        }

        private void addDayToStats(PeriodStats ps, DayData d, boolean countUsed) {
            for (int position = 0; position < BASE_TILE_COUNT; position++) {
                int subtype = PyramidScheme.subtypeForPosition(position);
                int category = CATEGORY_BY_SUBTYPE[subtype];
                int group = GROUP_BY_POSITION[position];
                boolean drinks = subtype == PyramidScheme.SUBTYPE_DRINKS;
                if (!drinks) ps.totalByCat[category]++;
                ps.totalByGroup[group]++;
                if (countUsed && d.ticks[position]) {
                    if (!drinks) ps.usedByCat[category]++;
                    ps.usedByGroup[group]++;
                }
            }
            if (countUsed) {
                for (int subtype = 0; subtype < PyramidScheme.SUBTYPE_COUNT; subtype++) {
                    int extra = d.subtypeExtras[subtype];
                    int category = CATEGORY_BY_SUBTYPE[subtype];
                    int group = GROUP_BY_SUBTYPE[subtype];
                    boolean drinks = subtype == PyramidScheme.SUBTYPE_DRINKS;
                    if (!drinks) {
                        ps.usedByCat[category] += extra;
                        ps.usedExtraByCat[category] += extra;
                    }
                    ps.usedByGroup[group] += extra;
                }
            }
        }

        private int[] colorCountsForDay(DayData d, boolean includeTotal) {
            int[] counts = new int[3];
            for (int position = 0; position < BASE_TILE_COUNT; position++) {
                int subtype = PyramidScheme.subtypeForPosition(position);
                if (includeTotal || d.ticks[position]) {
                    counts[CATEGORY_BY_SUBTYPE[subtype]]++;
                }
            }
            for (int subtype = 0; subtype < PyramidScheme.SUBTYPE_COUNT; subtype++) {
                counts[CATEGORY_BY_SUBTYPE[subtype]] += d.subtypeExtras[subtype];
            }
            return counts;
        }

        private int[] groupsForCategory(int cat) {
            if (cat == CAT_RED) return new int[] { GROUP_EXTRAS };
            if (cat == CAT_WATER) return new int[] { GROUP_DRINKS };
            if (cat == CAT_YELLOW) {
                return new int[] {
                        GROUP_OILS_FATS,
                        GROUP_NUTS_SEEDS,
                        GROUP_MILK_DAIRY,
                        GROUP_PROTEIN
                };
            }
            return new int[] { GROUP_GRAINS_SIDES, GROUP_PRODUCE };
        }

        private int visualCategoryForSubtype(int subtype) {
            return subtype == PyramidScheme.SUBTYPE_DRINKS ? CAT_WATER : CATEGORY_BY_SUBTYPE[subtype];
        }

        private int groupIconType(int group) {
            switch (group) {
                case GROUP_EXTRAS: return TYPE_DESSERT;
                case GROUP_OILS_FATS: return TYPE_OIL;
                case GROUP_NUTS_SEEDS: return TYPE_NUTS;
                case GROUP_MILK_DAIRY: return TYPE_MILK_CHEESE;
                case GROUP_PROTEIN: return TYPE_PROTEIN;
                case GROUP_GRAINS_SIDES: return TYPE_WHEAT_POTATO;
                case GROUP_PRODUCE: return TYPE_PRODUCE;
                case GROUP_DRINKS: return TYPE_WATER;
                default: return TYPE_EXTRA;
            }
        }

        private int typeForPosition(int position) {
            if (position == 2) return TYPE_BUTTER;
            return typeForSubtype(PyramidScheme.subtypeForPosition(position));
        }

        private int typeForSubtype(int subtype) {
            switch (subtype) {
                case PyramidScheme.SUBTYPE_EXTRAS: return TYPE_DESSERT;
                case PyramidScheme.SUBTYPE_OILS_FATS: return TYPE_OIL;
                case PyramidScheme.SUBTYPE_NUTS_SEEDS: return TYPE_NUTS;
                case PyramidScheme.SUBTYPE_MILK_DAIRY: return TYPE_MILK_CHEESE;
                case PyramidScheme.SUBTYPE_PROTEIN: return TYPE_PROTEIN;
                case PyramidScheme.SUBTYPE_GRAINS: return TYPE_WHEAT;
                case PyramidScheme.SUBTYPE_SIDES: return TYPE_WHEAT_POTATO;
                case PyramidScheme.SUBTYPE_PRODUCE: return TYPE_PRODUCE;
                case PyramidScheme.SUBTYPE_DRINKS: return TYPE_WATER;
                default: return TYPE_EXTRA;
            }
        }

        private boolean isExpanded(int key) {
            Boolean b = expandedStats.get(key);
            return b != null && b;
        }

        private void showSnackbar(String message) {
            snackbarText = message;
            snackbarUntil = System.currentTimeMillis() + 2600;
        }


        private boolean isPastelCozy() {
            return skinStyle == SKIN_PASTEL_COZY;
        }

        private void drawLeafSprig(Canvas c, float x, float y, float size, int col) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(alpha(col, 115));
            c.drawLine(x, y, x + size * .9f, y - size * .8f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(col, 95));
            for (int i = 0; i < 3; i++) {
                float px = x + size * (.22f + i * .22f);
                float py = y - size * (.18f + i * .16f);
                c.drawOval(new RectF(px - size*.12f, py - size*.06f, px + size*.12f, py + size*.10f), p);
            }
        }

        private void drawLambBeeAvatar(Canvas c, RectF r) {
            drawBiPrideIconBackground(c, r);

            float cx = r.centerX();
            float cy = r.centerY();
            float s = r.width();

            // wings
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.WHITE, 180));
            c.drawOval(new RectF(cx - s*.46f, cy + s*.03f, cx - s*.12f, cy + s*.34f), p);
            c.drawOval(new RectF(cx + s*.12f, cy + s*.03f, cx + s*.46f, cy + s*.34f), p);

            // bee hood and body
            p.setColor(Color.rgb(255, 211, 93));
            c.drawCircle(cx, cy - s*.03f, s*.29f, p);
            p.setColor(Color.rgb(255, 216, 105));
            c.drawRoundRect(new RectF(cx - s*.24f, cy + s*.16f, cx + s*.24f, cy + s*.43f), dp(7), dp(7), p);
            p.setColor(Color.rgb(44, 38, 31));
            c.drawRect(cx - s*.22f, cy + s*.25f, cx + s*.22f, cy + s*.31f, p);

            // ears
            p.setColor(Color.rgb(255, 244, 225));
            c.drawOval(new RectF(cx - s*.43f, cy - s*.05f, cx - s*.20f, cy + s*.13f), p);
            c.drawOval(new RectF(cx + s*.20f, cy - s*.05f, cx + s*.43f, cy + s*.13f), p);
            p.setColor(Color.rgb(244, 159, 148));
            c.drawOval(new RectF(cx - s*.38f, cy + s*.00f, cx - s*.24f, cy + s*.09f), p);
            c.drawOval(new RectF(cx + s*.24f, cy + s*.00f, cx + s*.38f, cy + s*.09f), p);

            // wool face
            p.setColor(Color.rgb(255, 248, 235));
            c.drawCircle(cx, cy, s*.23f, p);
            for (int i = 0; i < 7; i++) {
                double a = -Math.PI*.9 + i * Math.PI*.3;
                c.drawCircle(cx + (float)Math.cos(a)*s*.17f, cy - s*.18f + (float)Math.sin(a)*s*.04f, s*.075f, p);
            }

            // antennae
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.rgb(54, 45, 36));
            c.drawLine(cx - s*.11f, cy - s*.31f, cx - s*.20f, cy - s*.45f, p);
            c.drawLine(cx + s*.11f, cy - s*.31f, cx + s*.20f, cy - s*.45f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 210, 72));
            c.drawCircle(cx - s*.22f, cy - s*.47f, s*.045f, p);
            c.drawCircle(cx + s*.22f, cy - s*.47f, s*.045f, p);

            // face
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(78, 59, 48));
            c.drawArc(new RectF(cx - s*.11f, cy - s*.04f, cx - s*.02f, cy + s*.05f), 20, 140, false, p);
            c.drawArc(new RectF(cx + s*.02f, cy - s*.04f, cx + s*.11f, cy + s*.05f), 20, 140, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(238, 142, 132));
            c.drawCircle(cx, cy + s*.035f, s*.025f, p);

            // small carrot
            Path carrot = new Path();
            carrot.moveTo(cx + s*.19f, cy + s*.18f);
            carrot.lineTo(cx + s*.36f, cy + s*.15f);
            carrot.lineTo(cx + s*.26f, cy + s*.27f);
            carrot.close();
            p.setColor(Color.rgb(239, 136, 54));
            c.drawPath(carrot, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.4f));
            p.setColor(Color.rgb(82, 151, 72));
            c.drawLine(cx + s*.20f, cy + s*.17f, cx + s*.15f, cy + s*.08f, p);
        }

        private void drawBiPrideIconBackground(Canvas c, RectF r) {
            float radius = dp(18);
            p.setStyle(Paint.Style.FILL);
            LinearGradient gradient = new LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    new int[] { Color.rgb(247, 146, 181), Color.rgb(190, 153, 229), Color.rgb(142, 194, 239) },
                    new float[] { 0f, .52f, 1f },
                    Shader.TileMode.CLAMP);
            p.setShader(gradient);
            c.drawRoundRect(r, radius, radius, p);
            p.setShader(null);

            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.WHITE, 128));
            c.drawCircle(r.left + r.width()*.20f, r.top + r.height()*.24f, r.width()*.045f, p);
            c.drawCircle(r.right - r.width()*.20f, r.top + r.height()*.25f, r.width()*.045f, p);

            drawSmallFlower(c, r.left + r.width()*.22f, r.top + r.height()*.33f, r.width()*.13f);
            drawSmallFlower(c, r.right - r.width()*.23f, r.top + r.height()*.36f, r.width()*.12f);
            drawHeart(c, new RectF(r.left + r.width()*.16f, r.top + r.height()*.55f, r.left + r.width()*.28f, r.top + r.height()*.69f), Color.rgb(239, 115, 147));
        }

        private void drawSmallFlower(Canvas c, float cx, float cy, float s) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Color.WHITE, 210));
            for (int i = 0; i < 5; i++) {
                double a = Math.PI * 2 * i / 5.0;
                c.drawOval(new RectF(cx + (float)Math.cos(a)*s*.35f - s*.18f, cy + (float)Math.sin(a)*s*.35f - s*.12f,
                        cx + (float)Math.cos(a)*s*.35f + s*.18f, cy + (float)Math.sin(a)*s*.35f + s*.12f), p);
            }
            p.setColor(Color.rgb(239, 194, 80));
            c.drawCircle(cx, cy, s*.14f, p);
        }

        private void updatePalette() {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = themeMode == MODE_DARK || (themeMode == MODE_SYSTEM && night == Configuration.UI_MODE_NIGHT_YES);
            if (isPastelCozy()) {
                if (dark) {
                    bg = Color.rgb(31, 28, 33);
                    card = Color.rgb(46, 41, 49);
                    primaryText = Color.rgb(232, 220, 209);
                    secondaryText = Color.rgb(212, 194, 181);
                    accent = Color.rgb(178, 209, 151);
                    outline = Color.rgb(91, 78, 88);
                    red = Color.rgb(194, 103, 119);
                    yellow = Color.rgb(222, 183, 98);
                    green = Color.rgb(129, 158, 103);
                    waterBlue = Color.rgb(83, 145, 184);
                } else {
                    bg = Color.rgb(255, 249, 238);
                    card = Color.rgb(255, 239, 218);
                    primaryText = Color.rgb(81, 69, 57);
                    secondaryText = Color.rgb(126, 105, 88);
                    accent = Color.rgb(104, 154, 112);
                    outline = Color.rgb(226, 198, 171);
                    red = Color.rgb(240, 145, 155);
                    yellow = Color.rgb(240, 202, 118);
                    green = Color.rgb(158, 194, 128);
                    waterBlue = Color.rgb(143, 207, 235);
                }
            } else {
                if (dark) {
                    bg = Color.rgb(30, 34, 31);
                    card = Color.rgb(42, 48, 44);
                    primaryText = Color.rgb(234, 235, 228);
                    secondaryText = Color.rgb(188, 199, 184);
                    accent = Color.rgb(147, 197, 114);
                    outline = Color.rgb(75, 87, 74);
                    red = Color.rgb(182, 82, 89);
                    yellow = Color.rgb(206, 168, 72);
                    green = Color.rgb(111, 153, 90);
                    waterBlue = Color.rgb(65, 126, 166);
                } else {
                    bg = Color.rgb(247, 250, 244);
                    card = Color.WHITE;
                    primaryText = Color.rgb(43, 50, 41);
                    secondaryText = Color.rgb(92, 107, 88);
                    accent = Color.rgb(78, 143, 85);
                    outline = Color.rgb(211, 226, 207);
                    red = Color.rgb(238, 111, 120);
                    yellow = Color.rgb(244, 201, 89);
                    green = Color.rgb(143, 192, 115);
                    waterBlue = Color.rgb(150, 210, 236);
                }
            }
        }

        private int categoryColor(int cat) {
            return cat == CAT_RED ? red : cat == CAT_YELLOW ? yellow : cat == CAT_WATER ? waterBlue : green;
        }

        private int categoryBorder(int cat) {
            int base = categoryColor(cat);
            return dark ? darken(base, 0.65f) : darken(base, 0.82f);
        }

        private int darken(int color, float f) {
            return Color.rgb((int)(Color.red(color)*f), (int)(Color.green(color)*f), (int)(Color.blue(color)*f));
        }

        private int alpha(int color, int a) {
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
        }

        private int desaturate(int color, float saturation, float brightness) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[1] *= saturation;
            hsv[2] = Math.min(1f, hsv[2] * brightness);
            return Color.HSVToColor(hsv);
        }

        private int iconColor(int color, boolean muted) {
            if (!muted) return color;
            return alpha(desaturate(color, 0.25f, dark ? 0.8f : 1.08f), dark ? 160 : 185);
        }

        private RectF inset(RectF r, float dx, float dy) {
            return new RectF(r.left + dx, r.top + dy, r.right - dx, r.bottom - dy);
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        private boolean isRtl() {
            return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        }

        private Paint.Align startAlign() {
            return isRtl() ? Paint.Align.RIGHT : Paint.Align.LEFT;
        }

        private Paint.Align endAlign() {
            return isRtl() ? Paint.Align.LEFT : Paint.Align.RIGHT;
        }

        private float startX(RectF bounds, float inset) {
            return isRtl() ? bounds.right - inset : bounds.left + inset;
        }

        private float startX(RectF bounds, float inset, float width) {
            return isRtl() ? bounds.right - inset - width : bounds.left + inset;
        }

        private float endX(RectF bounds, float inset) {
            return isRtl() ? bounds.left + inset : bounds.right - inset;
        }

        private float endX(RectF bounds, float inset, float width) {
            return isRtl() ? bounds.left + inset : bounds.right - inset - width;
        }

        private RectF startRect(float left, float right, float inset, float width, float top, float height) {
            float x = isRtl() ? right - inset - width : left + inset;
            return new RectF(x, top, x + width, top + height);
        }

        private RectF endRect(float left, float right, float inset, float width, float top, float height) {
            float x = isRtl() ? left + inset : right - inset - width;
            return new RectF(x, top, x + width, top + height);
        }

        private float calendarCellLeft(float left, float right, float width, int column) {
            return isRtl() ? right - (column + 1) * width : left + column * width;
        }

        private float calendarCellCenter(float left, float right, float width, int column) {
            return calendarCellLeft(left, right, width, column) + width / 2f;
        }

        private float systemBottomInset() {
            if (Build.VERSION.SDK_INT >= 23) {
                WindowInsets wi = getRootWindowInsets();
                if (wi != null) return wi.getStableInsetBottom();
            }
            return 0f;
        }

        private float systemTopInset() {
            if (Build.VERSION.SDK_INT >= 23) {
                WindowInsets wi = getRootWindowInsets();
                if (wi != null) return wi.getStableInsetTop();
            }
            return 0f;
        }

        private String dateHeader(LocalDate d) {
            String month = d.getMonth().getDisplayName(TextStyle.SHORT, appLocale());
            if (d.equals(LocalDate.now())) {
                return s(R.string.date_today_format, d.getDayOfMonth(), month);
            }
            return s(R.string.date_full_format,
                    d.getDayOfWeek().getDisplayName(TextStyle.FULL, appLocale()),
                    d.getDayOfMonth(),
                    month);
        }

        private String monthHeader(LocalDate d) {
            return s(R.string.month_year_format,
                    d.getMonth().getDisplayName(TextStyle.FULL, appLocale()),
                    d.getYear());
        }

        private String s(int resourceId, Object... arguments) {
            return arguments.length == 0
                    ? getResources().getString(resourceId)
                    : getResources().getString(resourceId, arguments);
        }

        private String[] a(int resourceId) {
            return getResources().getStringArray(resourceId);
        }

        private Locale appLocale() {
            Configuration configuration = getResources().getConfiguration();
            if (!configuration.getLocales().isEmpty()) {
                return configuration.getLocales().get(0);
            }
            return Locale.GERMAN;
        }

        private void drawDessert(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(248, 227, 172), muted));
            c.drawRoundRect(new RectF(r.left, r.centerY(), r.centerX(), r.bottom), dp(4), dp(4), p);
            p.setColor(iconColor(Color.rgb(246, 136, 151), muted));
            c.drawRoundRect(new RectF(r.centerX(), r.top + r.height() * .35f, r.right, r.bottom), dp(4), dp(4), p);
            p.setColor(iconColor(Color.rgb(255, 250, 235), muted));
            c.drawCircle(r.left + r.width() * .27f, r.centerY(), r.width() * .18f, p);
            p.setColor(iconColor(Color.rgb(238, 82, 87), muted));
            c.drawCircle(r.right - r.width() * .2f, r.top + r.height() * .28f, r.width() * .07f, p);
            drawCuteFace(c, r.centerX(), r.centerY() + r.height() * .18f, r.width() * .75f, muted);
        }

        private void drawOil(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(129, 167, 86), muted));
            c.drawOval(new RectF(r.left + r.width()*.05f, r.centerY(), r.left + r.width()*.45f, r.bottom), p);
            c.drawOval(new RectF(r.centerX() - r.width()*.12f, r.top + r.height()*.35f, r.centerX() + r.width()*.28f, r.bottom), p);
            c.drawOval(new RectF(r.right - r.width()*.42f, r.top + r.height()*.18f, r.right - r.width()*.02f, r.bottom - r.height()*.12f), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(iconColor(Color.rgb(70, 120, 62), muted));
            c.drawLine(r.left + r.width()*.12f, r.bottom - r.height()*.06f, r.right - r.width()*.05f, r.top + r.height()*.15f, p);
        }

        private void drawButter(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(255, 222, 88), muted));
            c.drawRoundRect(new RectF(r.left + r.width()*.12f, r.top + r.height()*.35f, r.right - r.width()*.12f, r.bottom - r.height()*.18f), dp(4), dp(4), p);
            p.setColor(iconColor(Color.rgb(235, 246, 255), muted));
            c.drawRoundRect(new RectF(r.left + r.width()*.05f, r.bottom - r.height()*.18f, r.right - r.width()*.05f, r.bottom - r.height()*.08f), dp(3), dp(3), p);
            drawCuteFace(c, r.centerX(), r.centerY() + r.height()*.14f, r.width()*0.65f, muted);
        }

        private void drawMilkCheese(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(238, 250, 255), muted));
            c.drawRoundRect(new RectF(r.left + r.width()*.08f, r.top + r.height()*.08f, r.left + r.width()*.42f, r.bottom - r.height()*.05f), dp(3), dp(3), p);
            p.setColor(iconColor(Color.rgb(255, 217, 74), muted));
            Path cheese = new Path();
            cheese.moveTo(r.centerX(), r.centerY());
            cheese.lineTo(r.right - r.width()*.08f, r.top + r.height()*.36f);
            cheese.lineTo(r.right - r.width()*.08f, r.bottom - r.height()*.10f);
            cheese.lineTo(r.centerX(), r.bottom - r.height()*.12f);
            cheese.close();
            c.drawPath(cheese, p);
            drawCuteFace(c, r.centerX() + r.width()*.18f, r.centerY() + r.height()*.18f, r.width()*0.55f, muted);
        }

        private void drawNutsSeeds(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(187, 123, 72), muted));
            c.drawOval(new RectF(
                    r.left + r.width() * .08f,
                    r.top + r.height() * .24f,
                    r.left + r.width() * .50f,
                    r.bottom - r.height() * .12f), p);
            p.setColor(iconColor(Color.rgb(222, 166, 96), muted));
            c.drawOval(new RectF(
                    r.centerX() - r.width() * .02f,
                    r.top + r.height() * .08f,
                    r.right - r.width() * .08f,
                    r.bottom - r.height() * .22f), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.4f));
            p.setColor(iconColor(Color.rgb(125, 78, 49), muted));
            c.drawLine(
                    r.left + r.width() * .28f,
                    r.top + r.height() * .32f,
                    r.left + r.width() * .30f,
                    r.bottom - r.height() * .20f,
                    p);
            c.drawLine(
                    r.centerX() + r.width() * .20f,
                    r.top + r.height() * .18f,
                    r.centerX() + r.width() * .13f,
                    r.bottom - r.height() * .30f,
                    p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(115, 151, 83), muted));
            c.drawCircle(r.right - r.width() * .12f, r.bottom - r.height() * .14f, r.width() * .055f, p);
            c.drawCircle(r.right - r.width() * .28f, r.bottom - r.height() * .08f, r.width() * .045f, p);
            c.drawCircle(r.centerX(), r.bottom - r.height() * .07f, r.width() * .04f, p);
        }

        private void drawProteinGroup(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(123, 178, 105), muted));
            RectF pod = new RectF(
                    r.left + r.width() * .03f,
                    r.top + r.height() * .18f,
                    r.centerX() + r.width() * .12f,
                    r.bottom - r.height() * .14f);
            c.drawOval(pod, p);
            p.setColor(iconColor(Color.rgb(218, 238, 185), muted));
            for (int i = 0; i < 3; i++) {
                c.drawCircle(
                        pod.left + pod.width() * (.25f + i * .25f),
                        pod.centerY(),
                        r.width() * .055f,
                        p);
            }

            p.setColor(iconColor(Color.rgb(225, 156, 133), muted));
            c.drawRoundRect(new RectF(
                    r.centerX() + r.width() * .10f,
                    r.top + r.height() * .10f,
                    r.right - r.width() * .04f,
                    r.centerY() + r.height() * .06f), dp(5), dp(5), p);

            p.setColor(iconColor(Color.rgb(255, 248, 225), muted));
            c.drawOval(new RectF(
                    r.centerX() + r.width() * .08f,
                    r.centerY() + r.height() * .02f,
                    r.right,
                    r.bottom), p);
            p.setColor(iconColor(Color.rgb(245, 190, 70), muted));
            c.drawCircle(
                    r.centerX() + r.width() * .30f,
                    r.centerY() + r.height() * .25f,
                    r.width() * .09f,
                    p);
        }

        private void drawWheat(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.2f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(iconColor(Color.rgb(217, 170, 50), muted));
            float x = r.centerX();
            c.drawLine(x, r.bottom, x, r.top + r.height()*0.1f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(240, 198, 74), muted));
            for (int i = 0; i < 5; i++) {
                float yy = r.bottom - r.height()*(0.15f + i*0.15f);
                c.drawOval(new RectF(x - r.width()*0.28f, yy - r.height()*0.09f, x, yy + r.height()*0.02f), p);
                c.drawOval(new RectF(x, yy - r.height()*0.09f, x + r.width()*0.28f, yy + r.height()*0.02f), p);
            }
        }

        private void drawWheatPotato(Canvas c, RectF r, boolean muted) {
            drawWheat(c, new RectF(r.left, r.top, r.centerX(), r.bottom), muted);
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(202, 143, 76), muted));
            c.drawOval(new RectF(r.centerX(), r.centerY(), r.right, r.bottom), p);
            drawCuteFace(c, r.centerX() + r.width()*.25f, r.centerY() + r.height()*.28f, r.width()*.5f, muted);
        }

        private void drawApple(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(229, 67, 60), muted));
            c.drawCircle(r.centerX() - r.width()*0.12f, r.centerY() + r.height()*0.08f, r.width()*0.22f, p);
            c.drawCircle(r.centerX() + r.width()*0.12f, r.centerY() + r.height()*0.08f, r.width()*0.22f, p);
            p.setColor(iconColor(Color.rgb(92, 142, 74), muted));
            c.drawOval(new RectF(r.centerX(), r.top, r.right - r.width()*0.12f, r.top + r.height()*0.25f), p);
            drawCuteFace(c, r.centerX(), r.centerY() + r.height()*0.15f, r.width()*0.7f, muted);
        }

        private void drawCarrot(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(237, 131, 52), muted));
            Path carrot = new Path();
            carrot.moveTo(r.centerX(), r.bottom - r.height() * .05f);
            carrot.lineTo(r.left + r.width() * .24f, r.top + r.height() * .34f);
            carrot.lineTo(r.right - r.width() * .18f, r.top + r.height() * .30f);
            carrot.close();
            c.drawPath(carrot, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(iconColor(Color.rgb(73, 151, 76), muted));
            c.drawLine(r.centerX(), r.top + r.height() * .32f, r.centerX(), r.top, p);
            c.drawLine(r.centerX(), r.top + r.height() * .32f, r.centerX() - r.width() * .2f, r.top + r.height() * .06f, p);
            c.drawLine(r.centerX(), r.top + r.height() * .32f, r.centerX() + r.width() * .2f, r.top + r.height() * .06f, p);
            drawCuteFace(c, r.centerX(), r.centerY() + r.height() * .13f, r.width() * .8f, muted);
        }

        private void drawWater(Canvas c, RectF r, boolean muted) {
            Path glass = new Path();
            glass.moveTo(r.left + r.width() * .25f, r.top + r.height() * .08f);
            glass.lineTo(r.right - r.width() * .25f, r.top + r.height() * .08f);
            glass.lineTo(r.right - r.width() * .34f, r.bottom - r.height() * .05f);
            glass.lineTo(r.left + r.width() * .34f, r.bottom - r.height() * .05f);
            glass.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(211, 245, 252), muted));
            c.drawPath(glass, p);
            p.setColor(iconColor(Color.rgb(103, 200, 236), muted));
            c.drawRect(r.left + r.width() * .34f, r.top + r.height() * .45f, r.right - r.width() * .34f, r.bottom - r.height() * .16f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.8f));
            p.setColor(iconColor(Color.rgb(59, 151, 196), muted));
            c.drawPath(glass, p);
            drawCuteFace(c, r.centerX(), r.centerY() + r.height() * .18f, r.width() * .7f, muted);
        }

        private void drawX(Canvas c, RectF r) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            float pad = Math.max(dp(3), Math.min(dp(8), r.width() * .22f));
            p.setStrokeWidth(Math.max(dp(1.8f), Math.min(dp(3.6f), r.width() * .11f)));
            p.setColor(dark ? alpha(Color.WHITE, 170) : alpha(Color.rgb(72, 67, 61), 150));
            c.drawLine(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad, p);
            c.drawLine(r.right - pad, r.top + pad, r.left + pad, r.bottom - pad, p);
        }

        private void drawGear(Canvas c, RectF r, int col) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.2f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(col);
            float cx = r.centerX(), cy = r.centerY();
            c.drawCircle(cx, cy, dp(8), p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4.0;
                float x1 = cx + (float)Math.cos(a) * dp(12);
                float y1 = cy + (float)Math.sin(a) * dp(12);
                float x2 = cx + (float)Math.cos(a) * dp(15);
                float y2 = cy + (float)Math.sin(a) * dp(15);
                c.drawLine(x1, y1, x2, y2, p);
            }
            c.drawCircle(cx, cy, dp(2.5f), p);
        }

        private void drawChevron(Canvas c, RectF r, boolean left, int col) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.5f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(col);
            Path path = new Path();
            if (left) {
                path.moveTo(r.centerX() + dp(5), r.centerY() - dp(8));
                path.lineTo(r.centerX() - dp(5), r.centerY());
                path.lineTo(r.centerX() + dp(5), r.centerY() + dp(8));
            } else {
                path.moveTo(r.centerX() - dp(5), r.centerY() - dp(8));
                path.lineTo(r.centerX() + dp(5), r.centerY());
                path.lineTo(r.centerX() - dp(5), r.centerY() + dp(8));
            }
            c.drawPath(path, p);
        }

        private void drawBulb(Canvas c, float cx, float cy, int col) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(col);
            c.drawCircle(cx, cy - dp(3), dp(7), p);
            c.drawLine(cx - dp(4), cy + dp(6), cx + dp(4), cy + dp(6), p);
            c.drawLine(cx - dp(3), cy + dp(10), cx + dp(3), cy + dp(10), p);
        }

        private void drawHeart(Canvas c, RectF r, int col) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(col);
            Path heart = new Path();
            heart.moveTo(r.centerX(), r.bottom - dp(5));
            heart.cubicTo(r.left, r.centerY(), r.left + dp(3), r.top, r.centerX(), r.centerY() - dp(2));
            heart.cubicTo(r.right - dp(3), r.top, r.right, r.centerY(), r.centerX(), r.bottom - dp(5));
            c.drawPath(heart, p);
        }

        private void drawCalendarIcon(Canvas c, float cx, float cy, int col) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(col);
            RectF r = new RectF(cx - dp(11), cy - dp(10), cx + dp(11), cy + dp(12));
            c.drawRoundRect(r, dp(4), dp(4), p);
            c.drawLine(r.left, r.top + dp(6), r.right, r.top + dp(6), p);
        }

        private void drawSmallPyramidIcon(Canvas c, float cx, float cy, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            c.drawRect(cx - dp(12), cy + dp(6), cx + dp(12), cy + dp(10), p);
            c.drawRect(cx - dp(8), cy, cx + dp(8), cy + dp(4), p);
            c.drawRect(cx - dp(4), cy - dp(6), cx + dp(4), cy - dp(2), p);
        }

        private void drawBarsIcon(Canvas c, float cx, float cy, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            c.drawRoundRect(new RectF(cx - dp(12), cy + dp(3), cx - dp(7), cy + dp(12)), dp(2), dp(2), p);
            c.drawRoundRect(new RectF(cx - dp(2), cy - dp(5), cx + dp(3), cy + dp(12)), dp(2), dp(2), p);
            c.drawRoundRect(new RectF(cx + dp(8), cy - dp(10), cx + dp(13), cy + dp(12)), dp(2), dp(2), p);
        }

        private void drawExtraIcon(Canvas c, RectF r, boolean muted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(255, 255, 255), muted));
            c.drawCircle(r.centerX(), r.centerY(), r.width() * 0.22f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(iconColor(Color.rgb(95, 130, 85), muted));
            c.drawLine(r.centerX() - r.width()*0.12f, r.centerY(), r.centerX() + r.width()*0.12f, r.centerY(), p);
            c.drawLine(r.centerX(), r.centerY() - r.width()*0.12f, r.centerX(), r.centerY() + r.width()*0.12f, p);
        }

        private void drawCuteFace(Canvas c, float cx, float cy, float size, boolean muted) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(1.4f));
            p.setColor(iconColor(Color.rgb(65, 50, 40), muted));
            c.drawArc(new RectF(cx - size*.22f, cy - size*.08f, cx - size*.07f, cy + size*.07f), 20, 140, false, p);
            c.drawArc(new RectF(cx + size*.07f, cy - size*.08f, cx + size*.22f, cy + size*.07f), 20, 140, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(iconColor(Color.rgb(242, 141, 141), muted));
            c.drawCircle(cx, cy + size*.08f, size*.035f, p);
        }

        private void loadFoodBitmaps() {
            foodDessertBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_dessert);
            foodOilBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_oil);
            foodButterBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_butter);
            foodMilkCheeseBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_milk_cheese);
            foodProteinBmp = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.food_legumes_meat_fish_egg);
            foodWheatBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_wheat);
            foodWheatPotatoBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_wheat_potato);
            foodProduceBmp = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.food_fruit_vegetables);
            foodWaterBmp = BitmapFactory.decodeResource(getResources(), R.drawable.food_water);
            foodNutsSeedsBmp = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.food_nuts_seeds);
            aboutBrandBmp = BitmapFactory.decodeResource(getResources(), R.drawable.k2040_omnisexual_icon);
            appSymbolBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_lamb_bee_carrot);
            companionArtworkIdleBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_lamb_pose_idle);
            companionArtworkHappyBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_lamb_pose_happy);
            companionArtworkEatingBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_lamb_pose_eating);
            companionArtworkCuddleBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_lamb_pose_cuddle);
            companionArtworkSleepingBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_lamb_pose_sleeping);
            companionTreatBowlBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_prop_treat_bowl);
            companionPlayBallBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_prop_play_ball);
            companionCuddleHeartBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_effect_cuddle_heart);
            companionTokenFlowerBmp = BitmapFactory.decodeResource(
                    getResources(), R.drawable.companion_token_flower_bisexual);
        }

        private void ensureCompanionPageAssets() {
            CompanionVisualMode.Mode requiredMode = companionPageVisualMode();
            if (requiredMode == CompanionVisualMode.Mode.DAY
                    && (companionBackdropDayBmp == null || companionBarnLayerDayBmp == null)) {
                companionBackdropDayBmp = BitmapFactory.decodeResource(
                        getResources(),
                        R.drawable.companion_backdrop_day);
                companionBarnLayerDayBmp = BitmapFactory.decodeResource(
                        getResources(),
                        R.drawable.companion_barn_layer_day);
            }
            if (requiredMode == CompanionVisualMode.Mode.NIGHT
                    && (companionBackdropNightBmp == null || companionBarnLayerNightBmp == null)) {
                companionBackdropNightBmp = BitmapFactory.decodeResource(
                        getResources(),
                        R.drawable.companion_backdrop_night);
                companionBarnLayerNightBmp = BitmapFactory.decodeResource(
                        getResources(),
                        R.drawable.companion_barn_layer_night);
            }
        }

        private void releaseCompanionPageAssets() {
            companionBackdropDayBmp = null;
            companionBackdropNightBmp = null;
            companionBarnLayerDayBmp = null;
            companionBarnLayerNightBmp = null;
        }

        private void captureCompanionVisibleBounds(Bitmap bitmap, Rect outBounds) {
            outBounds.setEmpty();
            if (bitmap == null) return;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 0 || height <= 0) return;

            long pixelCount = (long) width * height;
            if (pixelCount > Integer.MAX_VALUE) return;
            int[] pixels = new int[(int) pixelCount];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            int left = width;
            int top = height;
            int right = -1;
            int bottom = -1;
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    if ((pixels[rowOffset + x] >>> 24) < COMPANION_BOUNDS_ALPHA_THRESHOLD) continue;
                    if (x < left) left = x;
                    if (y < top) top = y;
                    if (x > right) right = x;
                    if (y > bottom) bottom = y;
                }
            }
            if (right >= left && bottom >= top) {
                outBounds.set(
                        Math.max(0, left - COMPANION_BOUNDS_PADDING_PX),
                        Math.max(0, top - COMPANION_BOUNDS_PADDING_PX),
                        Math.min(width, right + 1 + COMPANION_BOUNDS_PADDING_PX),
                        Math.min(height, bottom + 1 + COMPANION_BOUNDS_PADDING_PX));
            }
        }

        private Bitmap bitmapForType(int type) {
            switch (type) {
                case TYPE_DESSERT: return foodDessertBmp;
                case TYPE_OIL: return foodOilBmp;
                case TYPE_BUTTER: return foodButterBmp;
                case TYPE_MILK_CHEESE: return foodMilkCheeseBmp;
                case TYPE_PROTEIN: return foodProteinBmp;
                case TYPE_WHEAT: return foodWheatBmp;
                case TYPE_WHEAT_POTATO: return foodWheatPotatoBmp;
                case TYPE_PRODUCE: return foodProduceBmp;
                case TYPE_WATER: return foodWaterBmp;
                case TYPE_NUTS: return foodNutsSeedsBmp;
                default: return null;
            }
        }

        private void drawRoundedBitmapIcon(Canvas c, Bitmap bmp, RectF dst, float radius) {
            if (bmp == null) return;
            Path clip = new Path();
            clip.addRoundRect(dst, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            drawBitmapIcon(c, bmp, dst, false);
            c.restore();

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawRoundRect(dst, radius, radius, p);
        }

        private void drawCircularBitmapIcon(Canvas c, Bitmap bmp, RectF dst) {
            if (bmp == null) return;

            float radius = Math.min(dst.width(), dst.height()) / 2f;
            Path clip = new Path();
            clip.addCircle(dst.centerX(), dst.centerY(), radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            drawBitmapIcon(c, bmp, dst, false);
            c.restore();

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(outline);
            c.drawCircle(dst.centerX(), dst.centerY(), radius, p);
        }

        private void drawBitmapIcon(Canvas c, Bitmap bmp, RectF dst, boolean muted) {
            if (bmp == null) return;
            float bw = bmp.getWidth();
            float bh = bmp.getHeight();
            if (bw <= 0 || bh <= 0) return;
            float scale = Math.min(dst.width() / bw, dst.height() / bh);
            float dw = bw * scale;
            float dh = bh * scale;
            RectF target = new RectF(dst.centerX() - dw / 2f, dst.centerY() - dh / 2f, dst.centerX() + dw / 2f, dst.centerY() + dh / 2f);
            if (muted) {
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0.12f);
                bitmapPaint.setColorFilter(new ColorMatrixColorFilter(cm));
                bitmapPaint.setAlpha(dark ? 150 : 175);
            } else {
                bitmapPaint.setColorFilter(null);
                bitmapPaint.setAlpha(255);
            }
            bitmapSrc.set(0, 0, bmp.getWidth(), bmp.getHeight());
            c.drawBitmap(bmp, bitmapSrc, target, bitmapPaint);
            bitmapPaint.setColorFilter(null);
            bitmapPaint.setAlpha(255);
        }

        private static final class BackupData {
            final String firstUsed;
            final Map<String, String> dayValues;
            final LocalDate selectedDate;
            final int themeMode;
            final int skinStyle;

            BackupData(String firstUsed, Map<String, String> dayValues, LocalDate selectedDate,
                       int themeMode, int skinStyle) {
                this.firstUsed = firstUsed;
                this.dayValues = dayValues;
                this.selectedDate = selectedDate;
                this.themeMode = themeMode;
                this.skinStyle = skinStyle;
            }
        }

        private static final class RowDef {
            final int category;
            final int[] subtypes;
            RowDef(int category, int[] subtypes) {
                this.category = category;
                this.subtypes = subtypes;
            }
        }

        private static final class DayData {
            final boolean[] ticks;
            final int[] subtypeExtras;
            DayData(boolean[] ticks, int[] subtypeExtras) {
                this.ticks = ticks;
                this.subtypeExtras = subtypeExtras;
            }
        }

        private static final class PeriodStats {
            final int[] usedByCat = new int[3];
            final int[] totalByCat = new int[3];
            final int[] usedByGroup = new int[GROUP_COUNT];
            final int[] totalByGroup = new int[GROUP_COUNT];
            final int[] usedExtraByCat = new int[3];
        }

        private static final class TouchTarget {
            final RectF rect;
            final Runnable action;
            TouchTarget(RectF rect, Runnable action) {
                this.rect = new RectF(rect);
                this.action = action;
            }
        }
    }
}
