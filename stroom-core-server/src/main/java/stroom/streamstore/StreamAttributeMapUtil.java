package stroom.streamstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.shared.Feed;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.date.DateUtil;

import java.util.HashMap;
import java.util.Map;

class StreamAttributeMapUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapUtil.class);

    private StreamAttributeMapUtil() {
        // Utility.
    }

    /**
     * Turns a stream attribute map object into a generic map of attributes for use by an expression filter.
     */
    static Map<String, Object> createAttributeMap(final StreamAttributeMap streamAttributeMap) {
        final Map<String, Object> attributeMap = new HashMap<>();

        final Stream stream = streamAttributeMap.getStream();
        if (stream != null) {
            attributeMap.put(StreamDataSource.STREAM_ID, stream.getId());
            attributeMap.put(StreamDataSource.CREATE_TIME, stream.getCreateMs());
            attributeMap.put(StreamDataSource.EFFECTIVE_TIME, stream.getEffectiveMs());
            attributeMap.put(StreamDataSource.STATUS_TIME, stream.getStatusMs());
            attributeMap.put(StreamDataSource.STATUS, stream.getStatus().getDisplayValue());
            if (stream.getParentStreamId() != null) {
                attributeMap.put(StreamDataSource.PARENT_STREAM_ID, stream.getParentStreamId());
            }
            if (stream.getStreamType() != null) {
                attributeMap.put(StreamDataSource.STREAM_TYPE_NAME, stream.getStreamType().getDisplayValue());
            }
            final Feed feed = stream.getFeed();
            if (feed != null) {
                attributeMap.put(StreamDataSource.FEED_NAME, feed.getName());
            }
            final StreamProcessor streamProcessor = stream.getStreamProcessor();
            if (streamProcessor != null) {
                final String pipelineUuid = streamProcessor.getPipelineUuid();
                if (pipelineUuid != null) {
                    attributeMap.put(StreamDataSource.PIPELINE_UUID, pipelineUuid);
                }
            }
        }

        StreamDataSource.getExtendedFields().forEach(field -> {
            final String value = streamAttributeMap.getAttributeValue(field.getName());
            if (value != null) {
                try {
                    switch (field.getType()) {
                        case FIELD:
                            attributeMap.put(field.getName(), value);
                            break;
                        case DATE_FIELD:
                            attributeMap.put(field.getName(), DateUtil.parseNormalDateTimeString(value));
                            break;
                        default:
                            attributeMap.put(field.getName(), Long.valueOf(value));
                            break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        });
        return attributeMap;
    }
}
