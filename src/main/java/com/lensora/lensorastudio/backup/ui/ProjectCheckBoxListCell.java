package com.lensora.lensorastudio.backup.ui;

import com.lensora.lensorastudio.model.Project;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

public class ProjectCheckBoxListCell extends ListCell<Project>
{
    private final CheckBox checkBox = new CheckBox();
    private final Label label = new Label();
    private final HBox container = new HBox(8, checkBox, label);
    private final ObservableSet<Project> checkedSet;

    public ProjectCheckBoxListCell(ObservableSet<Project> checkedSet)
    {
        this.checkedSet = checkedSet;
        container.setAlignment(Pos.CENTER_LEFT);

        checkBox.setOnAction(e -> handleToggle());
        label.setOnMouseClicked(e -> {
            checkBox.setSelected(!checkBox.isSelected());
            handleToggle();
        });
    }

    private void handleToggle()
    {
        Project item = getItem();
        if (item == null) return;

        if (checkBox.isSelected())
        {
            checkedSet.add(item);
        }
        else
        {
            checkedSet.remove(item);
        }
    }

    @Override
    protected void updateItem(Project project, boolean empty)
    {
        super.updateItem(project, empty);
        if (empty || project == null)
        {
            setText(null);
            setGraphic(null);
        }
        else
        {
            setText(null);
            label.setText(project.getProjectNumber() + " — " + project.getClientName());
            checkBox.setSelected(checkedSet.contains(project));
            setGraphic(container);
        }
    }
}