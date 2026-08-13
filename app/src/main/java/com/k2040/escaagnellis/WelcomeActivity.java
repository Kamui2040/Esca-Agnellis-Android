package com.k2040.escaagnellis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Mandatory first-launch acknowledgement using the shared cross-project welcome-screen structure.
 * App-specific wording, colours and legal content remain Esca Agnellis resources.
 */
public final class WelcomeActivity extends Activity {
    private static final String ACK_VERSION_KEY = "first_launch_notice_ack_version";
    private static final String EXTRA_ABOUT_MODE =
            "com.k2040.escaagnellis.extra.ABOUT_MODE";
    // Keep the existing acknowledgement contract. A visual redesign alone does not force re-acceptance.
    private static final int WELCOME_NOTICE_VERSION = 1;

    private static final int MODE_SYSTEM = 0;
    private static final int MODE_LIGHT = 1;
    private static final int MODE_DARK = 2;
    private static final int SKIN_STANDARD = 0;
    private static final int SKIN_PASTEL_COZY = 1;

    private SharedPreferences preferences;
    private Palette palette;
    private boolean aboutMode;

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
        configuration.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    public static Intent createAboutIntent(Context context) {
        return new Intent(context, WelcomeActivity.class)
                .putExtra(EXTRA_ABOUT_MODE, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PyramidScheme.PREFERENCES_NAME, MODE_PRIVATE);
        aboutMode = getIntent().getBooleanExtra(EXTRA_ABOUT_MODE, false);

        if (!aboutMode && preferences.getInt(ACK_VERSION_KEY, 0) >= WELCOME_NOTICE_VERSION) {
            launchMainActivity();
            return;
        }

        palette = Palette.resolve(this, preferences);
        applySystemBars();
        setContentView(aboutMode ? buildAboutScreen() : buildOnboardingScreen());
    }

    @Override
    public void onBackPressed() {
        if (aboutMode) {
            finish();
            return;
        }
        rejectWelcome();
    }

    private View buildOnboardingScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(palette.background);
        root.setPadding(dp(28), dp(20), dp(28), dp(20));

        ScrollView viewportScroll = new ScrollView(this);
        viewportScroll.setFillViewport(true);
        viewportScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root.addView(viewportScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout viewport = new LinearLayout(this);
        viewport.setOrientation(LinearLayout.VERTICAL);
        viewport.setGravity(Gravity.CENTER);
        viewportScroll.addView(viewport, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER_HORIZONTAL);
        shell.setPadding(dp(16), dp(14), dp(16), dp(12));
        shell.setBackground(cardBackground(palette.surface, palette.border, 24f));
        shell.setClipToOutline(true);

        LinearLayout.LayoutParams shellParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        shellParams.gravity = Gravity.CENTER_HORIZONTAL;
        viewport.addView(shell, shellParams);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.about_wolf);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setAdjustViewBounds(true);
        avatar.setContentDescription(getString(R.string.welcome_avatar_description));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        avatarParams.bottomMargin = dp(6);
        shell.addView(avatar, avatarParams);

        TextView title = centeredText(getString(R.string.app_name), 23f, palette.accent, true);
        LinearLayout.LayoutParams titleParams = matchWrapParams();
        titleParams.bottomMargin = dp(8);
        shell.addView(title, titleParams);

        TextView body = buildOnboardingBodyText();
        LinearLayout.LayoutParams bodyParams = matchWrapParams();
        bodyParams.bottomMargin = dp(10);
        shell.addView(body, bodyParams);

        Button accept = buildBottomButton(getString(R.string.welcome_action_continue), true);
        accept.setOnClickListener(v -> acceptWelcome());
        shell.addView(accept, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));

        return root;
    }

    private TextView buildOnboardingBodyText() {
        String value = getString(R.string.welcome_app_summary)
                + "\n\n"
                + getString(R.string.welcome_trust_summary)
                + "\n\n"
                + getString(R.string.welcome_licence_summary);

        SpannableString text = new SpannableString(value);
        addLicenceSpan(text, value, "GNU GPLv3", R.raw.gpl_3_0);
        addLicenceSpan(text, value, "CC BY 4.0", R.raw.cc_by_4_0);

        TextView body = centeredText("", 12.5f, palette.primaryText, false);
        body.setText(text);
        body.setLineSpacing(dp(2), 1f);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setHighlightColor(withAlpha(palette.accent, 38));
        return body;
    }

    private void addLicenceSpan(
            SpannableString text,
            String fullText,
            String licenceName,
            int rawResId) {
        int start = fullText.indexOf(licenceName);
        if (start < 0) {
            throw new IllegalStateException("Missing licence name in onboarding text: " + licenceName);
        }

        text.setSpan(
                new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        showBundledLicenseDialog(licenceName, rawResId);
                    }

                    @Override
                    public void updateDrawState(TextPaint drawState) {
                        drawState.setColor(palette.accent);
                        drawState.setUnderlineText(true);
                    }
                },
                start,
                start + licenceName.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private View buildAboutScreen() {
        Configuration configuration = getResources().getConfiguration();
        boolean constrainedLayout =
                configuration.screenWidthDp < 320 || configuration.fontScale > 1.15f;
        int rootHorizontalPadding = dp(constrainedLayout ? 12 : 28);
        int rootVerticalPadding = dp(20);
        int maxCardHeight = dp(520);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(palette.background);
        root.setPadding(
                rootHorizontalPadding,
                rootVerticalPadding,
                rootHorizontalPadding,
                rootVerticalPadding);

        FrameLayout shell = new FrameLayout(this);
        shell.setBackground(cardBackground(palette.surface, palette.border, 24f));
        shell.setClipToOutline(true);

        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int cardHeight = Math.min(
                maxCardHeight,
                Math.max(dp(240), displayHeight - (rootVerticalPadding * 2)));
        FrameLayout.LayoutParams shellParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                cardHeight);
        shellParams.gravity = Gravity.CENTER;
        root.addView(shell, shellParams);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            view.setPadding(
                    rootHorizontalPadding,
                    rootVerticalPadding + topInset,
                    rootHorizontalPadding,
                    rootVerticalPadding + bottomInset);

            int availableHeight = displayHeight
                    - topInset
                    - bottomInset
                    - (rootVerticalPadding * 2);
            int insetAwareCardHeight = Math.min(
                    maxCardHeight,
                    Math.max(dp(240), availableHeight));
            if (shellParams.height != insetAwareCardHeight) {
                shellParams.height = insetAwareCardHeight;
                shell.setLayoutParams(shellParams);
            }
            return insets;
        });
        root.requestApplyInsets();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        shell.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int contentHorizontalPadding = dp(constrainedLayout ? 10 : 14);
        content.setPadding(contentHorizontalPadding, dp(8), contentHorizontalPadding, dp(10));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.about_wolf);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setAdjustViewBounds(true);
        avatar.setContentDescription(getString(R.string.welcome_avatar_description));
        int avatarSize = dp(constrainedLayout ? 48 : 72);
        LinearLayout.LayoutParams avatarParams =
                new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.rightMargin = dp(constrainedLayout ? 8 : 10);
        header.addView(avatar, avatarParams);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        identity.setPadding(0, 0, dp(40), 0);

        TextView title = leftText(getString(R.string.app_name), 21f, palette.accent, true);
        title.setMaxLines(2);
        title.setHorizontallyScrolling(false);
        title.setAutoSizeTextTypeUniformWithConfiguration(
                14,
                21,
                1,
                TypedValue.COMPLEX_UNIT_SP);
        identity.addView(title, matchWrapParams());

        TextView versionNumber = leftText(
                BuildConfig.VERSION_NAME,
                11.5f,
                palette.secondaryText,
                false);
        versionNumber.setMaxLines(1);
        versionNumber.setHorizontallyScrolling(false);
        versionNumber.setAutoSizeTextTypeUniformWithConfiguration(
                9,
                12,
                1,
                TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams versionParams = matchWrapParams();
        versionParams.topMargin = dp(2);
        identity.addView(versionNumber, versionParams);

        header.addView(identity, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        LinearLayout.LayoutParams headerParams = matchWrapParams();
        headerParams.bottomMargin = dp(8);
        content.addView(header, headerParams);

        TextView description = leftText(
                getString(R.string.welcome_app_summary),
                13f,
                palette.primaryText,
                false);
        description.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descriptionParams = matchWrapParams();
        descriptionParams.bottomMargin = dp(3);
        content.addView(description, descriptionParams);

        TextView credit = leftText(
                getString(R.string.about_credit),
                11.5f,
                palette.secondaryText,
                false);
        credit.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams creditParams = matchWrapParams();
        creditParams.bottomMargin = dp(7);
        content.addView(credit, creditParams);

        TextView trust = leftText(
                getString(R.string.welcome_trust_summary),
                12.2f,
                palette.secondaryText,
                false);
        trust.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams trustParams = matchWrapParams();
        trustParams.bottomMargin = dp(9);
        content.addView(trust, trustParams);

        LinearLayout changelog = actionCard(getString(R.string.about_changelog));
        changelog.setOnClickListener(v -> showChangelogDialog());
        addWelcomeRow(content, changelog, 5);

        LinearLayout legal = actionCard(getString(R.string.about_license));
        legal.setOnClickListener(v -> showLicenseDialog());
        addWelcomeRow(content, legal, 5);

        LinearLayout sources = actionCard(getString(R.string.about_sources));
        sources.setOnClickListener(v -> showSourcesDialog());
        addWelcomeRow(content, sources, 5);

        LinearLayout support = actionCard(getString(R.string.about_support));
        support.setOnClickListener(v -> openExternalLink(
                R.string.support_url,
                R.string.welcome_support_unavailable));
        addWelcomeRow(content, support, 0);

        TextView close = centeredText(
                getString(R.string.symbol_close),
                24f,
                palette.secondaryText,
                false);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription(getString(R.string.welcome_action_close));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> finish());

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        closeParams.gravity = Gravity.END | Gravity.TOP;
        closeParams.topMargin = dp(1);
        closeParams.rightMargin = dp(1);
        shell.addView(close, closeParams);

        return root;
    }

    private void addWelcomeRow(LinearLayout parent, View row, int bottomMarginDp) {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(bottomMarginDp);
        parent.addView(row, params);
    }

    private LinearLayout actionCard(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(14), 0, dp(10), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(label);
        row.setBackground(clickableBackground(
                palette.elevatedSurface,
                palette.border,
                14f));

        TextView text = leftText(label, 14.5f, palette.primaryText, false);
        row.addView(text, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView chevron = centeredText("›", 23f, palette.accent, false);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(26), dp(42)));
        return row;
    }

    private Button buildBottomButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14f);
        button.setTypeface(Typeface.create("sans", primary ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextColor(palette.primaryText);
        button.setMinHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setMaxLines(1);
        button.setHorizontallyScrolling(false);
        button.setAutoSizeTextTypeUniformWithConfiguration(
                10,
                14,
                1,
                TypedValue.COMPLEX_UNIT_SP);
        button.setBackground(clickableBackground(
                palette.elevatedSurface,
                palette.border,
                15f));
        return button;
    }

    private TextView centeredText(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView leftText(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable cardBackground(int fill, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private Drawable clickableBackground(int fill, int stroke, float radiusDp) {
        GradientDrawable content = cardBackground(fill, stroke, radiusDp);
        return new RippleDrawable(
                ColorStateList.valueOf(withAlpha(palette.accent, 42)),
                content,
                null);
    }

    private void showChangelogDialog() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(8), dp(22), dp(18));

        for (String entry : getResources().getStringArray(R.array.about_changelog_entries)) {
            addLegalParagraph(body, "• " + entry, false);
        }

        showDedicatedDetailCard(BuildConfig.VERSION_NAME, body, 300);
    }

    private void showLicenseDialog() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(14));

        addLegalParagraph(body, getString(R.string.license_translation_notice), false);
        addLegalParagraph(body, getString(R.string.license_owner), false);

        body.addView(
                buildInfoSectionCard(
                        getString(R.string.license_code_heading),
                        getString(R.string.license_distribution),
                        getString(R.string.license_open_gpl),
                        v -> showBundledLicenseDialog(
                                getString(R.string.license_code_heading),
                                R.raw.gpl_3_0)),
                sectionParams());

        body.addView(
                buildInfoSectionCard(
                        getString(R.string.license_artwork_heading),
                        getString(R.string.license_artwork_summary),
                        getString(R.string.license_open_cc),
                        v -> showBundledLicenseDialog(
                                getString(R.string.license_artwork_heading),
                                R.raw.cc_by_4_0)),
                sectionParams());

        body.addView(
                buildInfoSectionCard(
                        getString(R.string.license_third_party_heading),
                        getString(R.string.license_third_party_summary),
                        getString(R.string.license_open_third_party),
                        v -> showBundledLicenseDialog(
                                getString(R.string.license_third_party_heading),
                                R.raw.third_party_notices)),
                sectionParams());

        String[] restrictions = getResources().getStringArray(R.array.license_restrictions);
        StringBuilder usage = new StringBuilder(getString(R.string.license_channels));
        for (int i = 2; i < restrictions.length; i++) {
            usage.append("\n\n").append(restrictions[i]);
        }
        usage.append("\n\n").append(getString(R.string.license_kofi));
        usage.append("\n\n").append(getString(R.string.license_bzfe));
        usage.append("\n\n").append(getString(R.string.license_rights));
        usage.append("\n\n").append(getString(R.string.license_disclaimer));

        body.addView(
                buildInfoSectionCard(
                        getString(R.string.license_usage_heading),
                        usage.toString(),
                        null,
                        null),
                sectionParams());

        showDedicatedDetailCard(getString(R.string.license_title), body, 520);
    }

    private void showSourcesDialog() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(6), dp(18), dp(16));

        addLegalParagraph(body, getString(R.string.about_sources_intro), false);

        LinearLayout bzfe = actionCard(
                getString(R.string.settings_bzfe_source)
                        + " · "
                        + getString(R.string.settings_bzfe_source_description));
        bzfe.setOnClickListener(v -> openExternalLink(
                R.string.bzfe_url,
                R.string.snackbar_bzfe_unavailable));
        body.addView(bzfe, matchWrapParams());

        showDedicatedDetailCard(getString(R.string.about_sources), body, 360);
    }

    private LinearLayout buildInfoSectionCard(
            String title,
            String bodyText,
            String actionLabel,
            View.OnClickListener action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(8));
        card.setBackground(cardBackground(
                palette.elevatedSurface,
                palette.border,
                14f));

        TextView heading = leftText(title, 14f, palette.accent, true);
        LinearLayout.LayoutParams headingParams = matchWrapParams();
        headingParams.bottomMargin = dp(6);
        card.addView(heading, headingParams);

        TextView description = leftText(bodyText, 12f, palette.primaryText, false);
        description.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descriptionParams = matchWrapParams();
        if (actionLabel != null) {
            descriptionParams.bottomMargin = dp(4);
        }
        card.addView(description, descriptionParams);

        if (actionLabel != null && action != null) {
            TextView open = leftText(actionLabel + " ›", 12.5f, palette.accent, false);
            open.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            open.setMinimumHeight(dp(48));
            open.setClickable(true);
            open.setFocusable(true);
            open.setContentDescription(actionLabel);
            open.setOnClickListener(action);
            card.addView(open, matchWrapParams());
        }

        return card;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(8);
        return params;
    }

    private void showBundledLicenseDialog(String title, int rawResId) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(8), dp(22), dp(18));

        TextView legalText = leftText(
                readBundledText(rawResId),
                11.5f,
                palette.primaryText,
                false);
        legalText.setLineSpacing(dp(2), 1f);
        legalText.setTextIsSelectable(true);
        body.addView(legalText, matchWrapParams());

        showDedicatedDetailCard(title, body, 520);
    }

    private String readBundledText(int rawResId) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getResources().openRawResource(rawResId),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read bundled licence text", error);
        }
        return text.toString();
    }

    private void showDedicatedDetailCard(String title, LinearLayout body, int maxHeightDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(palette.surface, palette.border, 24f));
        card.setClipToOutline(true);

        TextView heading = leftText(title, 20f, palette.primaryText, true);
        LinearLayout.LayoutParams headingParams = matchWrapParams();
        headingParams.leftMargin = dp(22);
        headingParams.topMargin = dp(18);
        headingParams.rightMargin = dp(22);
        headingParams.bottomMargin = dp(6);
        card.addView(heading, headingParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(6), dp(12), dp(8));

        Button back = buildBottomButton(getString(R.string.welcome_action_back), false);
        footer.addView(back, new LinearLayout.LayoutParams(dp(132), dp(48)));
        card.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout dialogRoot = new FrameLayout(this);
        dialogRoot.setPadding(dp(28), dp(24), dp(28), dp(24));

        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int cardHeight = Math.min(dp(maxHeightDp), displayHeight - dp(48));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                cardHeight);
        cardParams.gravity = Gravity.CENTER;
        dialogRoot.addView(card, cardParams);

        Dialog dialog = new Dialog(this, palette.dialogStyleRes);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogRoot);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        back.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.55f;
            window.setAttributes(attributes);
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void addLegalParagraph(LinearLayout parent, String value, boolean bold) {
        TextView paragraph = leftText(
                value,
                12f,
                bold ? palette.primaryText : palette.secondaryText,
                bold);
        paragraph.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(9);
        parent.addView(paragraph, params);
    }

    private void openExternalLink(int urlResId, int unavailableResId) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlResId))));
        } catch (Exception ignored) {
            Toast.makeText(this, unavailableResId, Toast.LENGTH_SHORT).show();
        }
    }

    private void acceptWelcome() {
        if (!preferences.edit().putInt(ACK_VERSION_KEY, WELCOME_NOTICE_VERSION).commit()) {
            Toast.makeText(this, R.string.welcome_ack_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        launchMainActivity();
    }

    private void rejectWelcome() {
        finishAndRemoveTask();
    }

    private void launchMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(0, 0);
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (palette.dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static final class Palette {
        final boolean dark;
        final int background;
        final int surface;
        final int elevatedSurface;
        final int accent;
        final int primaryText;
        final int secondaryText;
        final int border;
        final int primaryButtonText;
        final int dialogStyleRes;

        private Palette(
                boolean dark,
                int background,
                int surface,
                int elevatedSurface,
                int accent,
                int primaryText,
                int secondaryText,
                int border,
                int primaryButtonText,
                int dialogStyleRes) {
            this.dark = dark;
            this.background = background;
            this.surface = surface;
            this.elevatedSurface = elevatedSurface;
            this.accent = accent;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.border = border;
            this.primaryButtonText = primaryButtonText;
            this.dialogStyleRes = dialogStyleRes;
        }

        static Palette resolve(Context context, SharedPreferences preferences) {
            int themeMode = preferences.getInt("theme_mode", MODE_SYSTEM);
            int skinStyle = preferences.getInt("skin_style", SKIN_PASTEL_COZY);
            boolean systemDark = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            boolean dark = themeMode == MODE_DARK || (themeMode == MODE_SYSTEM && systemDark);
            boolean pastel = skinStyle != SKIN_STANDARD;

            if (pastel && dark) {
                return new Palette(
                        true,
                        Color.rgb(33, 29, 35),
                        Color.rgb(46, 41, 49),
                        Color.rgb(58, 51, 61),
                        Color.rgb(178, 209, 151),
                        Color.rgb(232, 220, 209),
                        Color.rgb(212, 194, 181),
                        Color.rgb(84, 76, 88),
                        Color.rgb(33, 29, 35),
                        R.style.EscaDialogPastelDark);
            }
            if (pastel) {
                return new Palette(
                        false,
                        Color.rgb(247, 231, 210),
                        Color.rgb(255, 239, 218),
                        Color.rgb(255, 229, 199),
                        Color.rgb(104, 154, 112),
                        Color.rgb(81, 69, 57),
                        Color.rgb(126, 105, 88),
                        Color.rgb(215, 194, 172),
                        Color.WHITE,
                        R.style.EscaDialogPastelLight);
            }
            if (dark) {
                return new Palette(
                        true,
                        Color.rgb(30, 35, 32),
                        Color.rgb(42, 48, 44),
                        Color.rgb(52, 59, 54),
                        Color.rgb(147, 197, 114),
                        Color.rgb(234, 235, 228),
                        Color.rgb(188, 199, 184),
                        Color.rgb(71, 80, 73),
                        Color.rgb(30, 35, 32),
                        R.style.EscaDialogStandardDark);
            }
            return new Palette(
                    false,
                    Color.rgb(247, 250, 244),
                    Color.WHITE,
                    Color.rgb(238, 244, 234),
                    Color.rgb(78, 143, 85),
                    Color.rgb(43, 50, 41),
                    Color.rgb(92, 107, 88),
                    Color.rgb(212, 221, 208),
                    Color.WHITE,
                    R.style.EscaDialogStandardLight);
        }
    }
}
