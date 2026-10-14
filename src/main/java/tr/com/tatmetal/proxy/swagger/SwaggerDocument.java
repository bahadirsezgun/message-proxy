package tr.com.tatmetal.proxy.swagger;

import java.net.URI;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Okunmuş bir OpenAPI 3 / Swagger 2 dokümanı.
 */
public class SwaggerDocument {

    /** springdoc grup adı; grup yoksa "-". */
    private final String group;
    /** Dokümanın okunduğu adres (göreli server adreslerini çözmek için). */
    private final URI documentUri;
    private final JsonNode root;

    public SwaggerDocument(String group, URI documentUri, JsonNode root) {
        this.group = group;
        this.documentUri = documentUri;
        this.root = root;
    }

    public String getGroup() {
        return group;
    }

    public URI getDocumentUri() {
        return documentUri;
    }

    public JsonNode getRoot() {
        return root;
    }

    @Override
    public String toString() {
        return "SwaggerDocument{group='" + group + "', documentUri=" + documentUri + '}';
    }
}
