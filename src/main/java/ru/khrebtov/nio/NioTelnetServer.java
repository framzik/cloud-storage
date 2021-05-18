package ru.khrebtov.nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class NioTelnetServer {
    public static final String LS_COMMAND = "\tls    view all files and directories\n";
    public static final String TOUCH_COMMAND = "\ttouch [filename] 	 create file\n";
    public static final String CD_COMMAND = "\tcd [path] 	 moving through the directory\n";
    public static final String RM_COMMAND = "\trm [filename | dirname] 	 remove file|directory\n";
    public static final String COPY_COMMAND = "\tcopy [src] [target]   copy file or directory\n";
    public static final String CAT_COMMAND = "\tcat [filename]   view content\n";
    public static final String MKDIR_COMMAND = "\tmkdir    create directory\n";
    public static final String CHANGE_NICKNAME = "\tnick    change nickname\n";

	private final ByteBuffer buffer = ByteBuffer.allocate(512);

	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(5678));
		server.configureBlocking(false);
		// OP_ACCEPT, OP_READ, OP_WRITE
		Selector selector = Selector.open();

		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");

		while (server.isOpen()) {
			selector.select();

			var selectionKeys = selector.selectedKeys();
			var iterator = selectionKeys.iterator();

			while (iterator.hasNext()) {
				var key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		SocketAddress client = channel.getRemoteAddress();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}

		buffer.flip();

		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}

		buffer.clear();

		// TODO
		// touch [filename] - создание файла
		// mkdir [dirname] - создание директории
		// cd [path] - перемещение по каталогу (.. | ~ )
		// rm [filename | dirname] - удаление файла или папки
		// copy [src] [target] - копирование файла или папки
		// cat [filename] - просмотр содержимого
		// вывод nickname в начале строки

		// NIO
		// NIO telnet server

		if (key.isValid()) {
			String command = sb
					.toString()
					.replace("\n", "")
					.replace("\r", "");

            if ("--help".equals(command)) {
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(TOUCH_COMMAND, selector, client);
                sendMessage(CD_COMMAND, selector, client);
                sendMessage(RM_COMMAND, selector, client);
                sendMessage(COPY_COMMAND, selector, client);
                sendMessage(CAT_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
                sendMessage(CHANGE_NICKNAME, selector, client);
            } else if ("ls".equals(command)) {
                sendMessage(getFileList().concat("\n"), selector, client);
            } else if (command.startsWith("touch")) {
                touch(selector, client, command);
            } else if (command.startsWith("mkdir")) {
                mkdir(selector, client, command);
            } else if (command.startsWith("rm")) {
                delete(command, selector, client);
            } else if (command.startsWith("cat")) {
                cat(command, selector, client);
            } else if (command.startsWith("copy")) {
                copy(command, selector, client);
            } else if ("exit".equals(command)) {
                System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
                channel.close();
                return;
            }
        }
    }

    private void copy(String command, Selector selector, SocketAddress client) throws IOException {
        String[] commands = command.split(" ");
        if(command.length()!=3){
            sendMessage("wrong command", selector, client);
        }else {
            Path srcPath = Path.of("server", commands[1]);
            Path dstPath = Path.of("server", commands[2]);
            if (!Files.exists(srcPath)){
                sendMessage("src file doesn't exist", selector, client);
            }
        }

    }

    private void cat(String command, Selector selector, SocketAddress client) throws IOException {
        String[] commands = command.split(" ");
        Path newPath = Path.of("server", commands[1]);

        Files.newBufferedReader(newPath).lines().forEach(m -> {
            try {
                sendMessage(m + System.lineSeparator(), selector, client);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void mkdir(Selector selector, SocketAddress client, String command) throws IOException {
        String[] commands = command.split(" ");
        Path newPath = Path.of("server", commands[1]);
        if (!Files.exists(newPath)) {
            Files.createDirectories(newPath);
            sendMessage("directory is created", selector, client);
        } else {
            sendMessage("directory is already exists", selector, client);
        }
    }

    private void touch(Selector selector, SocketAddress client, String command) throws IOException {
        String[] commands = command.split(" ");
        Path newPath = Path.of("server", commands[1]);
        if (!Files.exists(newPath)) {
            Files.createFile(newPath);
            sendMessage("file is created", selector, client);
        } else {
            sendMessage("file is already exists", selector, client);
        }
    }

    private void delete(String command, Selector selector, SocketAddress client) throws IOException {
        String[] commands = command.split(" ");
        Path newPath = Path.of("server", commands[1]);
        if (Files.exists(newPath)) {
            if (!Files.isDirectory(newPath)) {
                Files.delete(newPath);
                sendMessage("file was deleted", selector, client);
            } else {
                Files.walkFileTree(newPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                sendMessage("directory was deleted", selector, client);
            }
        } else sendMessage("directory/file doesn't exists", selector, client);
    }

    private String getFileList() {
        return String.join(" ", new File("server").list());
    }

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel)key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());

		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\n".getBytes(StandardCharsets.UTF_8)));
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}
