package me.xuxiaoxiao.chatapi.wechat;

import me.xuxiaoxiao.xtools.common.http.executor.impl.XRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;

final class WeChatTools {
    private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * 判断文件的media类型，共有三种，pic：图片，video：短视频，doc：其他文件
     *
     * @param file 需要判断的文件
     * @return 文件的media类型
     */
    @Nonnull
    public static String fileType(@Nullable File file) {
        switch (WeChatTools.fileSuffix(file)) {
            case "bmp":
            case "png":
            case "jpeg":
            case "jpg":
                return "pic";
            case "mp4":
                return "video";
            default:
                return "doc";
        }
    }

    /**
     * 获取文件的扩展名，图片类型的文件，会根据文件内容自动判断文件扩展名
     *
     * @param file 要获取文件扩展名的文件
     * @return 文件扩展名
     */
    @Nonnull
    public static String fileSuffix(@Nullable File file) {
        if (file != null) {
            try (FileInputStream is = new FileInputStream(file)) {
                byte[] b = new byte[3];
                if (is.read(b, 0, b.length) > 0) {
                    String fileCode = bytesToHex(b);
                    switch (fileCode) {
                        case "ffd8ff":
                            return "jpg";
                        case "89504e":
                            return "png";
                        case "474946":
                            return "gif";
                        default: {
                            String suffix = "";
                            if (fileCode.startsWith("424d")) {
                                suffix = "bmp";
                            } else if (file.getName().lastIndexOf('.') > 0) {
                                suffix = file.getName().substring(file.getName().lastIndexOf('.') + 1);
                            }
                            return suffix;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    /**
     * 将字节数组转换成16进制字符串
     *
     * @param bytes 要转换的字节数组
     * @return 转换后的字符串，全小写字母
     */
    @Nonnull
    private static String bytesToHex(@Nonnull byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            chars[i << 1] = HEX[b >>> 4 & 0xf];
            chars[(i << 1) + 1] = HEX[b & 0xf];
        }
        return new String(chars);
    }

    /**
     * 文件分片的请求体Part，微信在上传文件时，超过1M的文件，会进行分片上传，每片大小会根据网速等因素调整。
     * 为了简单起见，本库每片大小512KB
     */
    public static final class Slice extends XRequest.MultipartContent.Part {
        /**
         * 文件名称
         */
        @Nonnull
        public String fileName;
        /**
         * 文件的MIME类型
         */
        @Nonnull
        public String fileMime;
        /**
         * 字节数组内容的数量，字节数组大小总是512K而实际内容可能并没有这么多
         */
        public int count;

        /**
         * 文件分片构造器
         *
         * @param name     partName
         * @param fileName 文件名称
         * @param fileMime 文件Mime
         * @param slice    分片数组
         * @param count    分片数组中内容的数量
         */
        public Slice(@Nonnull String name, @Nonnull String fileName, @Nonnull String fileMime, @Nonnull byte[] slice, int count) {
            super(name, slice);
            this.fileName = fileName;
            this.fileMime = fileMime;
            this.count = count;
        }

        @Override
        @Nonnull
        public String[] headers() throws IOException {
            String disposition = String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"", name, URLEncoder.encode(fileName, "utf-8"));
            String type = String.format("Content-Type: %s", fileMime);
            return new String[]{disposition, type};
        }

        @Override
        public long partLength() {
            return count;
        }

        @Override
        public void partWrite(@Nonnull OutputStream doStream) throws IOException {
            if (value != null) {
                doStream.write((byte[]) value, 0, count);
            }
        }
    }
}
