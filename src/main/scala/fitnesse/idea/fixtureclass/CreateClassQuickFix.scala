package fitnesse.idea.fixtureclass

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.{CreateClassKind, CreateFromUsageUtils}
import com.intellij.codeInsight.intention.impl.{BaseIntentionAction, CreateClassDialog}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._

class CreateClassQuickFix(_refElement: FixtureClass) extends BaseIntentionAction {
  val elementPointer = SmartPointerManager.getInstance(_refElement.getProject).createSmartPsiElementPointer(_refElement)

  setText(getTitle(_refElement.fixtureClassName match {
    case Some(name) => name
    case None => "What's this class called?"
  }))

  def getRefElement: FixtureClass = elementPointer.getElement

  def getTitle(varName: String): String = QuickFixBundle.message("create.class.from.usage.text", CreateClassKind.CLASS.getDescription, varName)

  override def getFamilyName: String = QuickFixBundle.message("create.class.from.usage.family")

  override def startInWriteAction: Boolean = false

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    val element = getRefElement
    element != null && element.getManager.isInProject(element) && CreateFromUsageUtils.shouldShowTag(editor.getCaretModel.getOffset, element, element)
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val element = getRefElement
    if (element == null) return
    if (!FileModificationService.getInstance.preparePsiElementForWrite(element)) return

    askForTargetPackage(element, CreateClassKind.CLASS) match {
      case Some(directory) =>
        Option(CreateFromUsageUtils.createClass(CreateClassKind.CLASS, directory, element.fixtureClassName.get, element.getManager, element, element.getContainingFile, null)) match {
          case Some(aClass) =>
            ApplicationManager.getApplication.runWriteAction(new Runnable() {
              override def run() = {
                IdeDocumentHistory.getInstance(element.getProject).includeCurrentPlaceAsChangePlace()
                val descriptor = new OpenFileDescriptor(element.getProject, aClass.getContainingFile.getVirtualFile, aClass.getTextOffset)
                FileEditorManager.getInstance(aClass.getProject).openTextEditor(descriptor, true)
              }
            })
          case _ =>
        }
      case _ =>
    }
  }

  def askForTargetPackage(referenceElement: FixtureClass, classKind: CreateClassKind): Option[PsiDirectory] = {
    assert(!ApplicationManager.getApplication.isWriteAccessAllowed, "You must not run askForTargetPackage() from under write action")
    val manager = referenceElement.getManager
    val project = referenceElement.getProject
    val name = referenceElement.fixtureClassName.get
    val qualifierName = ""
    val sourceFile = referenceElement.getContainingFile
    val module = ModuleUtilCore.findModuleForPsiElement(sourceFile)
    val title = QuickFixBundle.message("create.class.title", StringUtil.capitalize(classKind.getDescription))
    val dialog = new CreateClassDialog(project, title, name, qualifierName, classKind, false, module)
    dialog.show()
    dialog.getExitCode match {
      case DialogWrapper.OK_EXIT_CODE => Some(dialog.getTargetDirectory)
      case _ => None
    }
  }
}
