package com.zhilin.common.exception;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.response.Result;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过真实 MVC 分发和校验验证接口契约；验收 Controller 仅在此测试上下文注册，不进入正式 JAR。
 */
@SpringBootTest(properties = "zhilin.auth.secret=test-only-signing-secret-never-use-in-deployment")
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.ContractCheckController.class)
class GlobalExceptionHandlerTest {

    @MockitoBean
    private AuthService authService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** 本类只测试 MVC 响应契约，身份校验单独由认证集成测试覆盖。 */
    @BeforeEach
    void prepareAuthenticatedRequest() {
        Mockito.when(authService.authenticate(ArgumentMatchers.anyString(),
                ArgumentMatchers.any())).thenReturn(new AuthSessionDTO(
                        1L, "contract-test", "contract-refresh", AuthClientEnum.USER,
                        Instant.now().plusSeconds(60)));
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer contract-test")).build();
    }

    @Autowired
    private MockMvc mockMvc;

    /** 成功数据保留 VO 结构，字符串 ID 不发生前端大整数精度损失。 */
    @Test
    void shouldReturnSuccessWithBusinessData() throws Exception {
        mockMvc.perform(get("/api/test-contracts").param("pageNum", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.id").value("9007199254740993"));
    }

    /** 空数据为 null，不能丢失 data 字段。 */
    @Test
    void shouldKeepNullDataField() throws Exception {
        mockMvc.perform(get("/api/test-contracts/empty"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":\"SUCCESS\",\"message\":\"操作成功\",\"data\":null}"));
    }

    /** 空集合与没有返回数据含义不同，应保留空数组。 */
    @Test
    void shouldKeepEmptyArray() throws Exception {
        mockMvc.perform(get("/api/test-contracts/empty-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    /** Controller 可在统一响应体之外正确选择创建成功的 201 状态。 */
    @Test
    void shouldReturnCreatedForValidBody() throws Exception {
        mockMvc.perform(post("/api/test-contracts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"验收标题\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.title").value("验收标题"));
    }

    /** DTO 约束失败时返回中文提示，不回显原始请求对象。 */
    @Test
    void shouldRejectInvalidBody() throws Exception {
        mockMvc.perform(post("/api/test-contracts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("标题不能为空"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /**
     * 验证请求体缺失或无法解析时返回安全的 400 响应。
     *
     * @param requestBody 参数化测试提供的缺失或非法 JSON 请求体
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"title\":{\"secret\":\"不应返回\"}}"})
    void shouldRejectUnreadableBody(String requestBody) throws Exception {
        mockMvc.perform(post("/api/test-contracts")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求体缺失或 JSON 格式错误"));
    }

    /**
     * 验证查询参数缺失、类型错误和约束不满足时均返回 400。
     *
     * @param path 携带缺失或非法查询参数的测试地址
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/test-contracts", "/api/test-contracts?pageNum=abc", "/api/test-contracts?pageNum=0"})
    void shouldRejectInvalidQuery(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    /** 业务状态冲突保持 409，不以成功响应掩盖失败。 */
    @Test
    void shouldReturnBusinessConflict() throws Exception {
        mockMvc.perform(get("/api/test-contracts/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    /**
     * 验证系统异常和返回值约束错误不会向客户端暴露内部信息。
     *
     * @param path 触发内部故障或非法返回值的测试地址
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/test-contracts/failure", "/api/test-contracts/invalid-result"})
    void shouldHideInternalFailure(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_SERVER_ERROR","message":"服务内部异常，请稍后重试","data":null}
                        """));
    }

    /** 不存在的接口也遵循统一 JSON 错误契约。 */
    @Test
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/not-existing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /** 405 响应保留 Allow 头，调用方仍可获知支持的请求方法。 */
    @Test
    void shouldKeepMethodNotAllowedHeader() throws Exception {
        mockMvc.perform(put("/api/test-contracts"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    /** Content-Type 错误应返回 415，而不是 JSON 解析错误或 500。 */
    @Test
    void shouldRejectUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/test-contracts").contentType(MediaType.TEXT_PLAIN).content("标题"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    /** 响应类型协商失败仍保持 406，不被兜底处理器改成 500。 */
    @Test
    void shouldRejectUnsupportedResponseType() throws Exception {
        mockMvc.perform(get("/api/test-contracts").param("pageNum", "1").accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotAcceptable());
    }

    /** 文档生成使用原生 OpenAPI 结构，并能解析统一响应的泛型 VO。 */
    @Test
    void shouldGenerateOpenApiWithoutResultWrapper() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("知邻社区后端接口"))
                .andExpect(jsonPath("$.paths['/api/test-contracts'].post.requestBody").exists())
                .andExpect(jsonPath("$.components.schemas.ContractCheckVO.properties.id.type").value("string"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    /** Knife4j 由依赖提供页面，无需建立本地 static 或 templates 目录。 */
    @Test
    void shouldServeKnife4jUi() throws Exception {
        mockMvc.perform(get("/doc.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Knife4j")));
    }

    /** Knife4j 必须能发现分组并取得 OpenAPI 3.0 文档，不能只返回一个页面空壳。 */
    @Test
    void shouldDiscoverKnife4jDocumentGroup() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[0].name").value("zhilin"))
                .andExpect(jsonPath("$.urls[0].url").value("/v3/api-docs/zhilin"));
        mockMvc.perform(get("/v3/api-docs/zhilin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.paths['/api/test-contracts'].post.requestBody").exists());
    }

    /** 仅供 MVC 契约验收使用，不模拟或实现文章业务。 */
    @TestComponent
    @RestController
    @RequestMapping("/api/test-contracts")
    static class ContractCheckController {

        /**
         * 校验页码并返回含字符串主键的验收对象。
         *
         * @param pageNum 从 1 开始的验收页码
         * @return 供序列化和参数校验测试使用的成功响应
         */
        @GetMapping
        Result<ContractCheckVO> query(@RequestParam @Min(value = 1, message = "页码必须大于等于 1") int pageNum) {
            return Result.success(new ContractCheckVO("9007199254740993", "验收标题"));
        }

        /**
         * 校验请求体并模拟创建成功，不写入业务数据。
         *
         * @param contractCheckDTO 带有非空标题约束的验收请求
         * @return HTTP 201 和包含验收对象的成功响应
         */
        @Operation(summary = "仅测试：验证请求校验和创建响应")
        @PostMapping
        ResponseEntity<Result<ContractCheckVO>> create(@Valid @RequestBody ContractCheckDTO contractCheckDTO) {
            ContractCheckVO contractCheckVO = new ContractCheckVO("9007199254740993", contractCheckDTO.title());
            return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(contractCheckVO));
        }

        /**
         * 验证无业务数据时保留 data 字段。
         *
         * @return data 为 null 的成功响应
         */
        @GetMapping("/empty")
        Result<Void> empty() {
            return Result.success(null);
        }

        /**
         * 验证空集合不会被转换为 null。
         *
         * @return data 为空数组的成功响应
         */
        @GetMapping("/empty-list")
        Result<List<ContractCheckVO>> emptyList() {
            return Result.success(List.of());
        }

        /**
         * 主动触发已知业务异常以验证状态映射。
         *
         * @return 此方法始终抛出异常，不产生正常返回值
         * @throws BusinessException 测试请求始终触发 CONFLICT
         */
        @GetMapping("/conflict")
        Result<Void> conflict() {
            throw new BusinessException(ErrorCodeEnum.CONFLICT);
        }

        /**
         * 注入包含内部信息的系统异常以验证安全响应。
         *
         * @return 此方法始终抛出异常，不产生正常返回值
         * @throws IllegalStateException 测试请求始终触发模拟系统故障
         */
        @GetMapping("/failure")
        Result<Void> failure() {
            throw new IllegalStateException("仅供日志排查的内部故障信息");
        }

        /**
         * 故意违反返回值约束以验证服务端错误处理。
         *
         * @return 固定返回 null，用于触发非空返回值校验
         */
        @NotNull(message = "不应公开的返回值约束")
        @GetMapping("/invalid-result")
        String invalidResult() {
            return null;
        }
    }

    /**
     * 仅供校验测试的请求模型。
     *
     * @param title 验收标题，不能为 null、空串或纯空白
     */
    record ContractCheckDTO(@NotBlank(message = "标题不能为空") String title) {
    }

    /**
     * 仅供序列化与文档测试的返回模型。
     *
     * @param id 字符串形式的验收资源 ID
     * @param title 验收标题
     */
    @Schema(description = "仅测试：契约验收返回对象")
    record ContractCheckVO(String id, String title) {
    }
}
