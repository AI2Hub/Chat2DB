
package ai.chat2db.server.web.api.controller.data.source.request;

/**
 * @author jipengfei
 * @version : DataSourceBaseRequestInfo.java
 */
public interface DataSourceBaseRequestInfo {

    /**
     * Get datasoure id
     * @return
     */
    Long getDataSourceId();

    /**
     * get datasoure name
     * @return
     */
    String getDatabaseName();
}