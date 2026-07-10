module com.lensora.lensorastudio
{
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;

    
    requires java.sql;
    requires java.prefs;
    requires transitive javafx.graphics;
    requires java.management;

    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;

    // UI
    requires atlantafx.base;
    requires javafx.base;

    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    // add icon pack modules
    requires org.kordamp.ikonli.fontawesome5;
    requires java.sql.rowset;

    exports com.lensora.lensorastudio;
    exports com.lensora.lensorastudio.controller;

    opens com.lensora.lensorastudio to javafx.fxml;
    opens com.lensora.lensorastudio.controller to javafx.fxml;
}