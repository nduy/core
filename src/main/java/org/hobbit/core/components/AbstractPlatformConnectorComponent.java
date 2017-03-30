/**
 * This file is part of core.
 *
 * core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with core.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.hobbit.core.components;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.hobbit.core.Commands;
import org.hobbit.core.data.RabbitQueue;
import org.hobbit.core.rabbit.RabbitMQUtils;

/**
 * This extension of the {@link AbstractCommandReceivingComponent} offers some
 * platform functionalities to other classes by implementing the
 * {@link PlatformConnector} interface.
 * 
 * @author Michael R&ouml;der (roeder@informatik.uni-leipzig.de)
 *
 */
public abstract class AbstractPlatformConnectorComponent extends AbstractCommandReceivingComponent
        implements PlatformConnector {

    private Map<String, ContainerStateObserver> containerObservers = new HashMap<String, ContainerStateObserver>();

    /**
     * {@inheritDoc}
     */
    @Override
    public RabbitQueue createDefaultRabbitQueue(String name) throws IOException {
        return super.createDefaultRabbitQueue(name);
    }

    @Override
    public String createContainer(String imageName, String[] envVariables, ContainerStateObserver observer) {
        if ((observer == null) || (imageName == null)) {
            return null;
        }
        String containerName = createContainer(imageName, envVariables);
        addContainerObserver(containerName, observer);
        return containerName;
    }

    @Override
    public void receiveCommand(byte command, byte[] data) {
        if (command == Commands.DOCKER_CONTAINER_TERMINATED) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            String containerName = RabbitMQUtils.readString(buffer);
            int exitCode = buffer.get();
            if (containerObservers.containsKey(containerName)) {
                containerObservers.get(containerName).containerStopped(containerName, exitCode);
                containerObservers.remove(containerName);
            }
        }
    }

    @Override
    public void stopContainer(String containerName) {
        super.stopContainer(containerName);
    }

    protected void addContainerObserver(String containerName, ContainerStateObserver observer) {
        if ((containerName != null) && (observer != null)) {
            containerObservers.put(containerName, observer);
        }
    }

}
