/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.itests.support;

import io.fabric8.api.gravia.ServiceLocator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.basic.AbstractCommand;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Function;
import org.junit.Assert;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test helper utility
 *
 * @since 03-Feb-2014
 */
public final class CommandSupport {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandSupport.class);

    // Hide ctor
    private CommandSupport() {
    }

    public static String executeCommands(String... commands) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(baos);

        CommandSession commandSession = getCommandSession(printStream);
        for (String cmdstr : commands) {
            System.out.println(cmdstr);
            executeCommand(cmdstr, commandSession);
        }

        printStream.flush();
        String result = baos.toString();
        System.out.println(result);
        return result;
    }

    public static String executeCommand(String cmdstr) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(baos);

        System.out.println(cmdstr);
        CommandSession commandSession = getCommandSession(printStream);
        executeCommand(cmdstr, commandSession);

        printStream.flush();
        String result = baos.toString();
        System.out.println(result);
        return result;
    }

    private static CommandSession getCommandSession(PrintStream printStream) {
        CommandSession commandSession;
        CommandProcessor commandProcessor = ServiceLocator.awaitService(CommandProcessor.class);
        commandSession = commandProcessor.createSession(System.in, printStream, printStream);
        commandSession.put("APPLICATION", System.getProperty("runtime.id"));
        commandSession.put("USER", "karaf");
        return commandSession;
    }

    private static void executeCommand(String cmdstr, CommandSession commandSession) {

        LOGGER.info(cmdstr);
        
        // When using the ssh:ssh command, the current ssh client calls KarafAgentFactory which expects the SSH_AUTH_SOCK env variable to be set, 
        // so work around the problem by registering the CommandSession in OSGi so that this variable is correctly initialised
        BundleContext syscontext = ServiceLocator.getSystemContext();
        ServiceRegistration<CommandSession> reg = syscontext.registerService(CommandSession.class, commandSession, null);

        try {
            // Get the command service
            List<String> tokens = Arrays.asList(cmdstr.split("\\s"));
            String[] header = tokens.get(0).split(":");
            Assert.assertTrue("Two tokens in: " + tokens.get(0), header.length == 2);
            String filter = "(&(osgi.command.scope=" + header[0] + ")(osgi.command.function=" + header[1] + "))";
            AbstractCommand command = (AbstractCommand) ServiceLocator.awaitService(Function.class, filter);
            commandSession.put(AbstractCommand.class.getName(), command);

            Class<?> actionClass = command.getActionClass();
            LOGGER.debug("Using action: {} from {}", actionClass, actionClass.getClassLoader());
            
            boolean keepRunning = true;
            while (!Thread.currentThread().isInterrupted() && keepRunning) {
                try {
                    commandSession.execute(cmdstr);
                    keepRunning = false;
                } catch (Exception ex) {
                    if (retryException(ex)) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException iex) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        throw new CommandExecutionException(ex);
                    }
                }
            }
        } finally {
            reg.unregister();
        }
    }

    private static boolean retryException(Exception ex) {
        //The gogo runtime package is not exported, so we are just checking against the class name.
        return ex.getClass().getName().equals("org.apache.felix.gogo.runtime.CommandNotFoundException");
    }

    public static abstract class SessionSupport implements CommandSession {

        private final InputStream keyboard;
        private final PrintStream console;
        private final Map<String, Object> properties = new HashMap<String, Object>();

        public SessionSupport(InputStream keyboard, PrintStream console) {
            this.keyboard = keyboard;
            this.console = console;
        }

        @Override
        public void close() {
        }

        @Override
        public Object convert(Class<?> arg0, Object arg1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public abstract Object execute(CharSequence arg0) throws Exception;

        @Override
        public CharSequence format(Object arg0, int arg1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(String key) {
            return properties.get(key);
        }

        @Override
        public void put(String key, Object value) {
            properties.put(key, value);
        }

        @Override
        public PrintStream getConsole() {
            return console;
        }

        @Override
        public InputStream getKeyboard() {
            return keyboard;
        }
    }
}
