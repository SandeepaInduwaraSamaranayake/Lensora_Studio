module com.lensora.lensorastudio{
    requires javafx.controls;
    requires javafx.fxml;

    requires atlantafx.base;
    requires java.sql;
    requires org.slf4j;
    requires java.prefs;
    requires transitive javafx.graphics;
    requires java.management;

    
    exports com.lensora.lensorastudio;
    exports com.lensora.lensorastudio.controller;

    opens com.lensora.lensorastudio to javafx.fxml;
    opens com.lensora.lensorastudio.controller to javafx.fxml;
}