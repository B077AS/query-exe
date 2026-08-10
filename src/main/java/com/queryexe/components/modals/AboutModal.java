package com.queryexe.components.modals;

import lombok.extern.slf4j.Slf4j;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import atlantafx.base.theme.Styles;
import com.queryexe.queryexe.App;

import java.io.IOException;
import java.io.InputStream;
import java.time.Year;
import java.util.Properties;

@Slf4j
public class AboutModal extends VBox {

    private static final Properties props = new Properties();
    private static final String APP_NAME;
    private static final String VERSION;
    private static final String TAGLINE;
    private static final String URL_GITHUB;
    private static final String URL_DOCS;
    private static final String URL_ISSUES;
    private static final String URL_WEBSITE;

    static {
        try (InputStream is = AboutModal.class.getResourceAsStream("/app.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            log.error("Could not load app.properties: " + e.getMessage());
        }
        APP_NAME = props.getProperty("app.name", "QueryExe");
        VERSION = props.getProperty("app.version", "Unknown");
        TAGLINE = props.getProperty("app.description", "");
        URL_GITHUB = props.getProperty("app.url.github", "");
        URL_DOCS = props.getProperty("app.url.docs", "");
        URL_ISSUES = props.getProperty("app.url.issues", "");
        URL_WEBSITE = props.getProperty("app.url.website", "");
    }

    public AboutModal() {
        this.setAlignment(Pos.TOP_CENTER);
        this.getStyleClass().add("modal-container");
        this.setMaxSize(650, 550);
        this.setMinSize(650, 550);
        this.setPrefSize(650, 550);
        this.setTranslateY(10);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_RIGHT);
        headerBox.setPadding(new Insets(10, 10, 0, 0));

        Region headerFillerRegion = new Region();

        Button closeButton = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        closeButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
        closeButton.setOnAction(event -> App.closeModal());

        headerBox.getChildren().addAll(headerFillerRegion, closeButton);
        HBox.setHgrow(headerFillerRegion, Priority.ALWAYS);

        VBox content = new VBox(10);
        content.setPadding(new Insets(0, 40, 0, 40));
        content.setAlignment(Pos.TOP_CENTER);

        ImageView appIcon = new ImageView();
        try {
            InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream, 80, 80, true, true);
                appIcon.setImage(icon);
            } else {
                log.error("Could not find icon.png in resources");
            }
        } catch (Exception e) {
            log.error("Error loading icon: " + e.getMessage());
        }

        Label appNameLabel = new Label(APP_NAME);
        appNameLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        Label versionLabel = new Label("Version " + VERSION);
        versionLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");

        Label taglineLabel = new Label(TAGLINE);
        taglineLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray; -fx-font-style: italic;");
        taglineLabel.setTextAlignment(TextAlignment.CENTER);

        Region separator1 = createSeparator();
        VBox systemInfoBox = createSystemInfoSection();
        Region separator2 = createSeparator();
        VBox linksBox = createLinksSection();
        Region separator3 = createSeparator();

        int currentYear = Year.now().getValue();
        Label copyrightLabel = new Label("© " + currentYear + " QueryExe");
        copyrightLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        copyrightLabel.setTextAlignment(TextAlignment.CENTER);
        copyrightLabel.setPadding(new Insets(0, 0, 10, 0));

        content.getChildren().addAll(
                appIcon,
                appNameLabel,
                versionLabel,
                taglineLabel,
                separator1,
                systemInfoBox,
                separator2,
                linksBox,
                separator3
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, scrollPane, copyrightLabel);
    }

    private VBox createSystemInfoSection() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("System Information");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox infoList = new VBox(5);
        infoList.setPadding(new Insets(5, 0, 0, 20));

        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String javafxVersion = System.getProperty("javafx.version", "Unknown");

        addInfoRow(infoList, "Java Version:", javaVersion);
        addInfoRow(infoList, "Java Vendor:", javaVendor);
        addInfoRow(infoList, "JavaFX Version:", javafxVersion);
        addInfoRow(infoList, "Operating System:", osName + " (" + osArch + ")");

        box.getChildren().addAll(titleLabel, infoList);
        return box;
    }

    private void addInfoRow(VBox container, String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label labelText = new Label(label);
        labelText.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 140px;");

        Label valueText = new Label(value);
        valueText.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        valueText.setWrapText(true);

        row.getChildren().addAll(labelText, valueText);
        container.getChildren().add(row);
    }

    private VBox createLinksSection() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Resources");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox linksRow = new HBox(15);
        linksRow.setAlignment(Pos.CENTER_LEFT);
        linksRow.setPadding(new Insets(5, 0, 0, 20));

        Hyperlink githubLink = createHyperlink("GitHub", MaterialDesignG.GITHUB, URL_GITHUB);
        Hyperlink docsLink = createHyperlink("Documentation", MaterialDesignB.BOOK_OPEN_PAGE_VARIANT, URL_DOCS);
        Hyperlink issuesLink = createHyperlink("Report Issue", MaterialDesignB.BUG, URL_ISSUES);
        Hyperlink websiteLink = createHyperlink("Website", MaterialDesignW.WEB, URL_WEBSITE);

        linksRow.getChildren().addAll(githubLink, docsLink, issuesLink, websiteLink);

        box.getChildren().addAll(titleLabel, linksRow);
        return box;
    }

    private Hyperlink createHyperlink(String text, Ikon icon, String url) {
        Hyperlink link = new Hyperlink(text, new FontIcon(icon));
        link.setStyle("-fx-font-size: 12px;");
        link.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (IOException ex) {
                log.error("Failed to open browser: " + ex.getMessage());
            }
        });
        return link;
    }

    private Region createSeparator() {
        Region separator = new Region();
        separator.setMinHeight(1);
        separator.setMaxHeight(1);
        separator.setStyle("-fx-background-color: -color-border-default;");
        return separator;
    }
}