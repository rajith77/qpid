/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.amqp_0_10.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;

public class OptionParser
{
    static final Option URL = new Option("u", "u", "connection url", "Qpid JMS Connection URL",
            "amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672'", String.class);

    static final Option HELP = new Option("h", "help", "show this help message and exit", null, null, Boolean.class);

    static final Option TIMEOUT = new Option("t", "timeout", "timeout in seconds to wait before exiting", "TIMEOUT",
            "0", Integer.class);

    private Map<String, Object> optMap = new HashMap<String, Object>();
    private static final List<Option> optDefs = new ArrayList<Option>();

    private String usage;
    private String desc;
    private String address;

    public OptionParser(String[] args, String usage, String desc)
    {
        this.usage = usage;
        this.desc = desc;

        if (args.length == 0 || (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help"))))
        {
            printHelp();
        }

        address = args[args.length - 1];
        String[] ops = new String[args.length - 1];
        System.arraycopy(args, 0, ops, 0, ops.length);
        parseOpts(ops);

        System.out.println(optMap);

        if (isHelp())
        {
            printHelp();
        }
    }

    public boolean isHelp()
    {
        return optMap.containsKey("h") || optMap.containsKey("help");
    }

    public void printHelp()
    {
        System.out.println(String.format("%s\n", usage));
        System.out.println(String.format("%s\n", desc));
        System.out.println(String.format("%s\n", "Options:"));

        for (Option op : optDefs)
        {
            String valueLabel = op.getValueLabel() != null ? "=" + op.getValueLabel() : "";
            String shortForm = op.getShortForm() != null ? "-" + op.getShortForm() + valueLabel : "";
            String longForm = op.getLongForm() != null ? "--" + op.getLongForm() + valueLabel : "";
            String desc = op.getDesc();
            String defaultValue = op.getDefaultValue() != null ? " (default " + op.getDefaultValue() + ")" : "";

            if (!shortForm.equals(""))
            {
                longForm = shortForm + ", " + longForm;
            }
            System.out.println(String.format("%-54s%s%s", longForm, desc, defaultValue));
        }

        System.exit(0);
    }

    private void parseOpts(String[] args)
    {
        String prevOpt = null;
        for (String op : args)
        {
            // covers both -h and --help formats
            if (op.startsWith("-"))
            {
                String key = op.substring(op.startsWith("--") ? 2 : 1,
                        (op.indexOf('=') > 0) ? op.indexOf('=') : op.length());

                boolean match = false;
                for (Option option : optDefs)
                {

                    if ((op.startsWith("-") && option.getShortForm() != null && option.getShortForm().equals(key))
                            || (op.startsWith("--") && option.getLongForm() != null && option.getLongForm().equals(key)))
                    {
                        match = true;
                        break;
                    }
                }

                if (!match)
                {
                    System.out.println(op + " is not a valid option");
                    System.exit(0);
                }

                if (op.indexOf('=') > 0)
                {
                    String val = extractValue(op.substring(op.indexOf('=') + 1));
                    if (optMap.containsKey(key))
                    {
                        optMap.put(key, optMap.get(key) + "," + val);
                    }
                    else
                    {
                        optMap.put(key, val);
                    }
                }
                else
                {
                    if (!optMap.containsKey(key))
                    {
                        optMap.put(key, "");
                    }
                    prevOpt = key;
                }
            }
            else if (prevOpt != null) // this is to catch broker localhost:5672
                                      // instead broker=localhost:5672
            {
                String val = extractValue(op);
                if (optMap.containsKey(prevOpt) && !optMap.get(prevOpt).toString().equals(""))
                {
                    optMap.put(prevOpt, optMap.get(prevOpt) + "," + val);
                }
                else
                {
                    optMap.put(prevOpt, val);
                }
                prevOpt = null;
            }
            else
            {
                System.out.println(optMap);
                throw new IllegalArgumentException(op + " is not a valid option");
            }
        }
    }

    private String extractValue(String op)
    {
        if (op.startsWith("'"))
        {
            if (!op.endsWith("'"))
            {
                throw new IllegalArgumentException(" The option " + op + " needs to be inside quotes");
            }

            return op.substring(1, op.length() - 1);
        }
        else
        {
            return op;
        }
    }

    protected boolean containsOp(Option op)
    {
        return optMap.containsKey(op.getShortForm()) || optMap.containsKey(op.getLongForm());
    }

    protected String getOp(Option op)
    {
        if (optMap.containsKey(op.getShortForm()))
        {
            return (String) optMap.get(op.getShortForm());
        }
        else if (optMap.containsKey(op.getLongForm()))
        {
            return (String) optMap.get(op.getLongForm());
        }
        else
        {
            return op.getDefaultValue();
        }
    }

    protected Connection createConnection() throws Exception
    {
        String url;
        if (containsOp(URL))
        {
            url = getOp(URL);
        }
        else
        {
            url = URL.getDefaultValue();
        }

        Connection con = new ConnectionImpl(url);
        return con;
    }

    public static void addOption(Option opt)
    {
        optDefs.add(opt);
    }

    protected String getAddress()
    {
        return address;
    }

    static class Option
    {
        private final String shortForm;
        private final String longForm;
        private final String desc;
        private final String valueLabel;
        private final String defaultValue;
        private final Class type;

        public Option(String shortForm, String longForm, String desc, String valueLabel, String defaultValue, Class type)
        {
            this.shortForm = shortForm;
            this.longForm = longForm;
            this.defaultValue = defaultValue;
            this.type = type;
            this.desc = desc;
            this.valueLabel = valueLabel;
        }

        public String getShortForm()
        {
            return shortForm;
        }

        public String getLongForm()
        {
            return longForm;
        }

        public String getDefaultValue()
        {
            return defaultValue;
        }

        public Class getType()
        {
            return type;
        }

        public String getDesc()
        {
            return desc;
        }

        public String getValueLabel()
        {
            return valueLabel;
        }
    }
}
