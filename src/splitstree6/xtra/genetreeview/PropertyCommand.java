package splitstree6.xtra.genetreeview;

import javafx.beans.property.Property;

public class PropertyCommand<T> extends SimpleCommand {

    public PropertyCommand (String name, Property<T> v, T oldValue, T newValue) {
        super(name,() -> v.setValue(oldValue),() -> v.setValue(newValue));
    }
}