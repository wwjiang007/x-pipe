package com.ctrip.xpipe.redis.keeper.applier;

import com.ctrip.xpipe.server.AbstractServer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Slight
 * <p>
 * Jun 01, 2022 08:19
 */
public abstract class AbstractInstanceNode extends AbstractServer {

    public Map<String, Object> dependencies;

    public List<AbstractInstanceComponent> components;

    @Override
    public void initialize() throws Exception {

        dependencies = new HashMap<>();
        components = new ArrayList();

        for (Field field : this.getClass().getFields()) {

            if (field.isAnnotationPresent(InstanceDependency.class)) {
                Object dependency = field.get(this);
                if (dependency instanceof InstanceComponentWrapper) {
                    dependency = ((InstanceComponentWrapper<?>) dependency).getInner();
                }
                dependencies.put(field.getName(), dependency);
            }

            Object value = field.get(this);

            if (AbstractInstanceComponent.class.isInstance(value)) {
                components.add((AbstractInstanceComponent) value);
            }
        }

        for (AbstractInstanceComponent component : components) {
            component.inject(dependencies);
        }

        super.initialize();
    }

    @Override
    protected void doInitialize() throws Exception {
        for (AbstractInstanceComponent component : components) {
            component.initialize();
        }
    }

    @Override
    protected void doStart() throws Exception {
        for (AbstractInstanceComponent component : components) {
            component.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (AbstractInstanceComponent component : components) {
            component.stop();
        }
    }

    @Override
    protected void doDispose() throws Exception {
        for (AbstractInstanceComponent component : components) {
            component.dispose();
        }
    }
}
