import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SiliconFlowRerankDemo {

    // =========================
    // 配置区：直接写在代码中
    // =========================
    private static final String API_KEY = "sk-dixbdqepxgjlqaxejzorbuxebhqpjalequwuwqyvnpxawczr";
    private static final String API_URL = "https://api.siliconflow.cn/v1/rerank";
    private static final String MODEL = "BAAI/bge-reranker-v2-m3";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String query = "王者荣耀中孙权的技能特点是什么？";

        List<String> documents = List.of(
                "孙权是一名以团队增益和持续输出为特点的英雄，技能可以强化普攻并提升队友作战能力。",
                "李白是一名高机动刺客，依靠位移和爆发伤害切入后排。",
                "孙权的被动技能坐断东南可以在战斗中积累状态，并增强后续技能效果。",
                "妲己主要依靠控制和单体爆发输出，适合蹲草秒杀敌方脆皮。"
        );

        String responseJson = rerank(query, documents, 4);

        System.out.println("原始响应：");
        System.out.println(responseJson);

        System.out.println("\n重排序结果：");
        printRerankResults(responseJson);
    }

    /**
     * 调用 SiliconFlow Rerank API
     */
    public static String rerank(String query, List<String> documents, int topN) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", topN);
        requestBody.put("return_documents", true);

        // 可选参数：长文档切分配置
        // BAAI/bge-reranker-v2-m3 支持 max_chunks_per_doc 和 overlap_tokens
        requestBody.put("max_chunks_per_doc", 1024);
        requestBody.put("overlap_tokens", 50);

        String jsonBody = MAPPER.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException(
                    "SiliconFlow API 调用失败，HTTP状态码: " + statusCode + "\n响应内容: " + body
            );
        }

        return body;
    }

    /**
     * 解析并打印 rerank 结果
     */
    private static void printRerankResults(String responseJson) throws Exception {
        JsonNode root = MAPPER.readTree(responseJson);
        JsonNode results = root.get("results");

        if (results == null || !results.isArray()) {
            System.out.println("响应中没有 results 字段。");
            return;
        }

        for (JsonNode item : results) {
            int index = item.path("index").asInt();
            double score = item.path("relevance_score").asDouble();

            String text = "";
            JsonNode documentNode = item.path("document");
            if (documentNode.has("text")) {
                text = documentNode.path("text").asText();
            }

            System.out.println("--------------------------------");
            System.out.println("原始文档下标: " + index);
            System.out.println("相关性得分: " + score);
            System.out.println("文档内容: " + text);
        }
    }
}