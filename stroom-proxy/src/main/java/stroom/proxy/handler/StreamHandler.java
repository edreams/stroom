package stroom.proxy.handler;

import stroom.feed.MetaMap;
import stroom.proxy.repo.StroomStreamHandler;

import java.io.IOException;

public interface StreamHandler extends StroomStreamHandler {
    void setMetaMap(MetaMap metaMap);

    void handleHeader() throws IOException;

    void handleFooter() throws IOException;

    void handleError() throws IOException;

    void validate();
}
