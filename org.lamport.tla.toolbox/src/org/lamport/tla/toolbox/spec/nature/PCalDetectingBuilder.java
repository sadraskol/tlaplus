package org.lamport.tla.toolbox.spec.nature;

import java.io.File;
import java.util.Map;

import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.lamport.tla.toolbox.Activator;
import org.lamport.tla.toolbox.spec.Spec;
import org.lamport.tla.toolbox.util.ResourceHelper;
import org.lamport.tla.toolbox.util.pref.IPreferenceConstants;

public class PCalDetectingBuilder extends IncrementalProjectBuilder
{

    public static final String BUILDER_ID = "toolbox.builder.PCalAlgorithmSearchingBuilder";
    private static final String PCAL_ALGORITHM_DEFINITION = "--algorithm";
    private static final String PCAL_FAIR_ALGORITHM_DEFINITION = "--fair";

    private PCalDetectingVisitor visitor = new PCalDetectingVisitor();

    /* (non-Javadoc)
     * @see org.eclipse.core.resources.IncrementalProjectBuilder#build(int, java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
     */
    protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map args, IProgressMonitor monitor) throws CoreException
    {
        final Spec spec = Activator.getSpecManager().getSpecLoaded();
        if (spec == null)
        {
            return null;
        }

        final IProject project = getProject();

        if (project != spec.getProject())
        {
            // skip the build calls on wrong projects (which are in WS, but not a current spec)
            return null;
        }

        project.accept(visitor);

        // must return null
        return null;
    }

    class PCalDetectingVisitor implements IResourceVisitor
    {
        /* (non-Javadoc)
         * @see org.eclipse.core.resources.IResourceVisitor#visit(org.eclipse.core.resources.IResource)
         */
		public boolean visit(IResource resource) throws CoreException {
			// check for resource existence (WS in-sync or out-of-sync)
			if (!resource.exists() || !new File(resource.getLocation().toOSString()).exists()) {
				return false;
			}
			if (IResource.PROJECT == resource.getType()) {
				return true;
			} else if (IResource.FILE == resource.getType() && ResourceHelper.isModule(resource)) {
				final IDocument document = getDocument(resource.getFullPath(), LocationKind.NORMALIZE);
				final FindReplaceDocumentAdapter searchAdapter = new FindReplaceDocumentAdapter(document);
				try {
					// matchRegion is set non-null iff there is a "--algorithm"
					// or "--fair"
					// string in the file. The "--fair" option added by LL on 6
					// July 2011.
					IRegion matchRegion = searchAdapter.find(0, PCAL_ALGORITHM_DEFINITION, true, true, false, false);
					if (matchRegion == null) {
						matchRegion = searchAdapter.find(0, PCAL_FAIR_ALGORITHM_DEFINITION, true, true, false, false);
					}

					// store the session property
					final QualifiedName key = new QualifiedName(Activator.PLUGIN_ID,
							IPreferenceConstants.CONTAINS_PCAL_ALGORITHM);

					// found a algorithm definition
					if (matchRegion != null) {
						resource.setSessionProperty(key, Boolean.TRUE);
					} else {
						resource.setSessionProperty(key, null);
					}
				} catch (BadLocationException e) {
					// do not swallow exceptions locally
					throw new CoreException(new Status(Status.ERROR, Activator.PLUGIN_ID,
							"Error trying to detect the algorithm", e));
				}
			}
			return false;
		}

		/**
		 * @param resource A {@link IResource} for which the corresponding {@link IDocument} should be received
		 */
		/**
		 * @param resourcePath The {@link IPath} to the {@link IResource} for which an {@link IDocument} should be received
		 * @param locKind The {@link LocationKind} of the {@link IPath}
		 * @return The corresponding {@link IDocument} or null
		 * @throws CoreException If the resource could not successfully be received
		 */
		private IDocument getDocument(final IPath resourcePath, final LocationKind locKind) throws CoreException {
			// connect the buffer manager to the given resource
			// the buffermanager takes care of cleanup/disconnecting 
			final ITextFileBufferManager bufferManager = ITextFileBufferManager.DEFAULT;
			bufferManager.connect(resourcePath, locKind, new NullProgressMonitor());

			// get the text file buf from the manager and receive the document
			final ITextFileBuffer itfb = bufferManager.getTextFileBuffer(resourcePath, locKind);
			return itfb.getDocument();
		}
    }
}
