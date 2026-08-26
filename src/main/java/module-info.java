module com.lensora.lensorastudio
{
    requires java.desktop;
    requires transitive  javafx.controls;
    requires javafx.fxml;

    
    requires java.sql;
    requires java.prefs;
    requires transitive javafx.graphics;
    requires transitive javafx.swing;
    requires java.management;

    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;

    // UI
    requires atlantafx.base;
    requires javafx.base;

    // Metadata
    requires com.drew.metadata;

    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    // add icon pack modules
    requires org.kordamp.ikonli.fontawesome5;
    requires java.sql.rowset;

    requires transitive org.snapfx;
    requires com.google.gson;
    requires ch.qos.logback.core;

    exports com.lensora.lensorastudio.app;
    exports com.lensora.lensorastudio.feature.backup.model;
    exports com.lensora.lensorastudio.core.context;

    // Opens for JavaFX FXML (controllers)
    opens com.lensora.lensorastudio.app to javafx.fxml;
    opens com.lensora.lensorastudio.ui.controller to javafx.fxml;
    opens com.lensora.lensorastudio.feature.backup.ui to javafx.fxml;
    opens com.lensora.lensorastudio.feature.explorer.controller to javafx.fxml;
    opens com.lensora.lensorastudio.feature.project.controller to javafx.fxml;
    opens com.lensora.lensorastudio.feature.settings.controller to javafx.fxml;

    // Opens for Gson serialization (models / settings)
    opens com.lensora.lensorastudio.core.config to com.google.gson;
    opens com.lensora.lensorastudio.feature.backup.model to com.google.gson;
    opens com.lensora.lensorastudio.feature.project.model to com.google.gson;
    opens com.lensora.lensorastudio.feature.settings.model to com.google.gson;
}