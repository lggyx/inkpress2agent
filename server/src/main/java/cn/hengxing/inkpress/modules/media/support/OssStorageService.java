package cn.hengxing.inkpress.modules.media.support;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 云对象存储（S3 兼容协议）：一套客户端通吃七牛/阿里云 OSS/腾讯 COS/MinIO 等的 S3 兼容端点。
 * 配置存 system_config（oss.*）；公网访问走 public_base_url（CDN/桶域名）+ key。
 */
@Service
public class OssStorageService {

    private final cn.hengxing.inkpress.modules.ai.service.AiConfigService configService;

    public OssStorageService(cn.hengxing.inkpress.modules.ai.service.AiConfigService configService) {
        this.configService = configService;
    }

    private String get(String key) {
        return configService.get(key);
    }

    public boolean configured() {
        return nz(get("oss.endpoint")) && nz(get("oss.bucket"))
                && nz(get("oss.access_key")) && nz(get("oss.secret_key"))
                && nz(get("oss.public_base_url"));
    }

    private static boolean nz(String s) {
        return s != null && !s.isBlank();
    }

    /** 生成对象 key：media/yyyyMM/uuid.ext */
    public String objectKey(String ext) {
        String month = LocalDate.now().toString().substring(0, 7).replace("-", "");
        return "media/" + month + "/" + java.util.UUID.randomUUID().toString().replace("-", "")
                + (ext == null || ext.isBlank() ? "" : (ext.startsWith(".") ? ext : "." + ext));
    }

    /** 公网访问链接：public_base_url 去尾斜杠 + "/" + key */
    public String publicUrl(String key) {
        String base = get("oss.public_base_url");
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + "/" + key;
    }

    /** 上传字节到 OSS，返回公网链接 */
    public String put(String key, byte[] bytes, String mime) {
        try (S3Client s3 = client()) {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(get("oss.bucket"))
                    .key(key)
                    .contentType(mime)
                    .build();
            s3.putObject(req, RequestBody.fromBytes(bytes));
            return publicUrl(key);
        } catch (Exception e) {
            throw cn.hengxing.inkpress.common.BizException.of(3009, "OSS 上传失败：" + e.getMessage());
        }
    }

    /** 测试连接：确保桶存在（无则创建）并写入探测对象 */
    public boolean test() {
        if (!configured()) {
            return false;
        }
        try (S3Client s3 = client()) {
            String bucket = get("oss.bucket");
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (Exception e) {
                // 404 等异常尝试创建（子账号有建桶权限时）
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            }
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key("inkpress-test.txt")
                            .contentType("text/plain").build(),
                    RequestBody.fromString("inkpress oss test " + System.currentTimeMillis()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private S3Client client() {
        return S3Client.builder()
                .endpointOverride(URI.create(get("oss.endpoint")))
                .region(Region.of(firstNonBlank(get("oss.region"), "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        get("oss.access_key"), get("oss.secret_key"))))
                .forcePathStyle(!"false".equalsIgnoreCase(firstNonBlank(get("oss.path_style"), "true")))
                .build();
    }

    private static String firstNonBlank(String a, String b) {
        return nz(a) ? a : b;
    }
}
