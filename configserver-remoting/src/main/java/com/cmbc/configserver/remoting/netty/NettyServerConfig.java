package com.cmbc.configserver.remoting.netty;

public class NettyServerConfig {
	private int listenPort = 8888;
	private int serverWorkerThreads = 32;
	private int serverCallbackExecutorThreads = 0;
	private int serverSelectorThreads = 3;
	private int serverOnewaySemaphoreValue = 32;
	private int serverAsyncSemaphoreValue = 64;
	private int serverChannelMaxIdleTimeSeconds = 120;
    private int serverMaxConnectionNumbers = 5*1000;

	public int getListenPort() {
		return listenPort;
	}

	public void setListenPort(int listenPort) {
		this.listenPort = listenPort;
	}

	public int getServerWorkerThreads() {
		return serverWorkerThreads;
	}

	public void setServerWorkerThreads(int serverWorkerThreads) {
		this.serverWorkerThreads = serverWorkerThreads;
	}

	public int getServerSelectorThreads() {
		return serverSelectorThreads;
	}

	public void setServerSelectorThreads(int serverSelectorThreads) {
		this.serverSelectorThreads = serverSelectorThreads;
	}

	public int getServerOnewaySemaphoreValue() {
		return serverOnewaySemaphoreValue;
	}

	public void setServerOnewaySemaphoreValue(int serverOnewaySemaphoreValue) {
		this.serverOnewaySemaphoreValue = serverOnewaySemaphoreValue;
	}

	public int getServerCallbackExecutorThreads() {
		return serverCallbackExecutorThreads;
	}

	public void setServerCallbackExecutorThreads(
			int serverCallbackExecutorThreads) {
		this.serverCallbackExecutorThreads = serverCallbackExecutorThreads;
	}

	public int getServerAsyncSemaphoreValue() {
		return serverAsyncSemaphoreValue;
	}

	public void setServerAsyncSemaphoreValue(int serverAsyncSemaphoreValue) {
		this.serverAsyncSemaphoreValue = serverAsyncSemaphoreValue;
	}

	public int getServerChannelMaxIdleTimeSeconds() {
		return serverChannelMaxIdleTimeSeconds;
	}

	public void setServerChannelMaxIdleTimeSeconds(
			int serverChannelMaxIdleTimeSeconds) {
		this.serverChannelMaxIdleTimeSeconds = serverChannelMaxIdleTimeSeconds;
	}

    public int getServerMaxConnectionNumbers() {
        return serverMaxConnectionNumbers;
    }

    public void setServerMaxConnectionNumbers(int serverMaxConnectionNumbers) {
        this.serverMaxConnectionNumbers = serverMaxConnectionNumbers;
    }
}