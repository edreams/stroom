package stroom.annotations.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.annotations.shared.AnnotationsIndex;
import stroom.annotations.shared.FindAnnotationsIndexCriteria;
import stroom.entity.server.ExternalDocumentEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.logging.DocumentEventLog;
import stroom.node.server.StroomPropertyService;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Component("annotationsIndexService")
@Transactional
public class AnnotationsIndexServiceImpl
        extends ExternalDocumentEntityServiceImpl<AnnotationsIndex, FindAnnotationsIndexCriteria>
        implements AnnotationsIndexService {
    @Inject
    AnnotationsIndexServiceImpl(final StroomEntityManager entityManager,
                                final ImportExportHelper importExportHelper,
                                final SecurityContext securityContext,
                                final DocumentEventLog documentEventLog,
                                final StroomPropertyService propertyService) {
        super(entityManager, importExportHelper, securityContext, documentEventLog, propertyService);
    }

    @Override
    public Class<AnnotationsIndex> getEntityClass() {
        return AnnotationsIndex.class;
    }

    @Override
    public FindAnnotationsIndexCriteria createCriteria() {
        return new FindAnnotationsIndexCriteria();
    }
}