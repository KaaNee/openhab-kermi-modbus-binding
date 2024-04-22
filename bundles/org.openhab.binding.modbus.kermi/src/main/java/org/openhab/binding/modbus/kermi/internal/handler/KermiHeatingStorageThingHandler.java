/*
 * Copyright (C) communicode AG - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * 2024
 */
package org.openhab.binding.modbus.kermi.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbus.handler.EndpointNotInitializedException;
import org.openhab.binding.modbus.handler.ModbusEndpointThingHandler;
import org.openhab.binding.modbus.kermi.internal.config.KermiConfiguration;
import org.openhab.core.io.transport.modbus.ModbusCommunicationInterface;
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@link KermiHeatingStorageThingHandler} Basic modbus connection to the Kermi device(s)
 *
 * @author Kai Neuhaus - Initial contribution
 */
@NonNullByDefault
public class KermiHeatingStorageThingHandler extends BaseBridgeHandler {

    public enum ReadStatus {
        NOT_RECEIVED,
        READ_SUCCESS,
        READ_FAILED
    }

    private final Logger logger = LoggerFactory.getLogger(KermiHeatingStorageThingHandler.class);

    private List<@Nullable PollTask> pollTasks = new ArrayList<>();
    private @Nullable KermiConfiguration config;

    /**
     * Communication interface to the slave endpoint we're connecting to
     */
    protected volatile @Nullable ModbusCommunicationInterface comms = null;
    private int slaveId;

    /**
     * @param bridge
     * @see BaseThingHandler
     */
    public KermiHeatingStorageThingHandler(final Bridge bridge) {
        super(bridge);
    }

    public @Nullable ModbusCommunicationInterface getComms() {
        return comms;
    }

    public int getSlaveId() {
        return slaveId;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // no control of Kermi device possible yet
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            KermiConfiguration localConfig = getConfigAs(KermiConfiguration.class);
            config = localConfig;

            if (config == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Kermi Configuration missing");
                return;
            }
            ModbusCommunicationInterface localComms = connectEndpoint();
            if (localComms != null) {

               // implement poller

            } // else state handling performed in connectEndPoint function
        });
    }

    /**
     * Get a reference to the modbus endpoint
     */
    private @Nullable ModbusCommunicationInterface connectEndpoint() {
        if (comms != null) {
            return comms;
        }

        ModbusEndpointThingHandler slaveEndpointThingHandler = getEndpointThingHandler();
        if (slaveEndpointThingHandler == null) {
            @SuppressWarnings("null")
            String label = Optional.ofNullable(getBridge()).map(b -> b.getLabel()).orElse("<null>");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    String.format("Bridge '%s' is offline", label));
            return null;
        }
        try {
            slaveId = slaveEndpointThingHandler.getSlaveId();
            comms = slaveEndpointThingHandler.getCommunicationInterface();
        } catch (EndpointNotInitializedException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    String.format("Slave Endpoint not initialized"));
            return null;
        }
        if (comms == null) {
            @SuppressWarnings("null")
            String label = Optional.ofNullable(getBridge()).map(b -> b.getLabel()).orElse("<null>");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    String.format("Bridge '%s' not completely initialized", label));
            return null;
        } else {
            return comms;
        }
    }

    /**
     * Get the endpoint handler from the bridge this handler is connected to
     * Checks that we're connected to the right type of bridge
     *
     * @return the endpoint handler or null if the bridge does not exist
     */
    private @Nullable ModbusEndpointThingHandler getEndpointThingHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("Bridge is null");
            return null;
        }
        if (bridge.getStatus() != ThingStatus.ONLINE) {
            logger.debug("Bridge is not online");
            return null;
        }

        ThingHandler handler = bridge.getHandler();
        if (handler == null) {
            logger.debug("Bridge handler is null");
            return null;
        }

        if (handler instanceof ModbusEndpointThingHandler thingHandler) {
            return thingHandler;
        } else {
            logger.debug("Unexpected bridge handler: {}", handler);
            return null;
        }
    }

    /**
     * Returns the channel UID for the specified group and channel id
     *
     * @param group String of channel group
     * @param id String the channel id in that group
     * @return the globally unique channel uid
     */
    private ChannelUID channelUID(Thing t, String group, String id) {
        return new ChannelUID(t.getUID(), group, id);
    }

    @Override
    public void dispose() {
        ModbusCommunicationInterface localComms = comms;
        if (localComms != null) {
            for (PollTask p : pollTasks) {
                PollTask localPoller = p;
                if (localPoller != null) {
                    localComms.unregisterRegularPoll(localPoller);
                }
            }
            /*
             * PollTask localInfoPoller = statePoller;
             * if (localInfoPoller != null) {
             * localComms.unregisterRegularPoll(localInfoPoller);
             * }
             * PollTask localDataPoller = dataPoller;
             * if (localDataPoller != null) {
             * localComms.unregisterRegularPoll(localDataPoller);
             * }
             */
        }
        // Comms will be close()'d by endpoint thing handler
        comms = null;
    }

    private void updateStatus() {
        logger.debug("Status update: State {} Data {} WorkHours {} PV {} ", stateRead, powerRead, workHoursRead,
                pvRead);
        if (stateRead != KermiXcenterThingHandler.ReadStatus.NOT_RECEIVED) { // && dataRead != ReadStatus.NOT_RECEIVED
            if (stateRead == KermiXcenterThingHandler.ReadStatus.READ_SUCCESS) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, STATE_READ_ERROR);
            }
            if (stateRead == alarmRead) {
                // both reads are ok or else both failed
                if (stateRead == KermiXcenterThingHandler.ReadStatus.READ_SUCCESS) {
                    updateStatus(ThingStatus.ONLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            STATE_AND_ALARM_READ_ERRORS);
                }
            } else {
                // either info or data read failed - update status with details
                if (stateRead == KermiXcenterThingHandler.ReadStatus.READ_FAILED) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, STATE_READ_ERROR);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, DATA_READ_ERROR);
                }
            }
        } // else - one status isn't received yet - wait until both Modbus polls returns either success or error
    }

}
