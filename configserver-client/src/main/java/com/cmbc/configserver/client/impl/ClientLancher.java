package com.cmbc.configserver.client.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.cmbc.configserver.client.ConfigClient;
import com.cmbc.configserver.domain.Configuration;
import com.cmbc.configserver.domain.Node;
import com.cmbc.configserver.remoting.netty.NettyClientConfig;

public class ClientLancher {
	public static void main(String[] args) throws IOException, InterruptedException {
		
        List<String> configServerAddress = new ArrayList<String>(1);
        configServerAddress.add("127.0.0.1:19999");
        ConfigClientImpl configClient = new ConfigClientImpl(new NettyClientConfig(), configServerAddress);
		
        Configuration config = new Configuration();
		Node node = new Node();
		node.setIp("127.0.0.1");
		node.setPort("21881");
		config.setNode(node);

		config.setCell("test-cell");
		config.setResource("test-dubbo-rpc");
		config.setType("publisher");
		
		boolean publishResult = configClient.publish(config);
		System.out.println(String.format("the result of publish config is %s",publishResult));
			
		
		System.in.read();
		
		
		configClient.close();
	}
}
