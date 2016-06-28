/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateHarvestingClientCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateHarvestingClientCommand;
import edu.harvard.iq.dataverse.harvest.client.HarvestingClient;
import edu.harvard.iq.dataverse.harvest.client.HarvestingClientServiceBean;
import edu.harvard.iq.dataverse.harvest.server.OAIRecord;
import edu.harvard.iq.dataverse.harvest.server.OAIRecordServiceBean;
import edu.harvard.iq.dataverse.harvest.server.OAISet;
import edu.harvard.iq.dataverse.harvest.server.OAISetServiceBean;
import edu.harvard.iq.dataverse.util.JsfHelper;
import static edu.harvard.iq.dataverse.util.JsfHelper.JH;
import edu.harvard.iq.dataverse.util.SystemConfig;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Leonid Andreev
 */
@ViewScoped
@Named
public class HarvestingSetsPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(HarvestingSetsPage.class.getCanonicalName());

    @Inject
    DataverseSession session;
    @EJB
    AuthenticationServiceBean authSvc;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    OAISetServiceBean oaiSetService;
    @EJB
    OAIRecordServiceBean oaiRecordService;
            
    @EJB
    EjbDataverseEngine engineService;
    @EJB
    SystemConfig systemConfig; 
    
    @Inject
    DataverseRequestServiceBean dvRequestService;
    @Inject
    NavigationWrapper navigationWrapper;
 
    private List<OAISet> configuredHarvestingSets;
    private OAISet selectedSet;
    private boolean setSpecValidated = false;
    
    public enum PageMode {

        VIEW, CREATE, EDIT
    }  
    private PageMode pageMode = PageMode.VIEW; 
    
    private int oaiServerStatusRadio; 
    
    private static final int oaiServerStatusRadioDisabled = 0;
    private static final int oaiServerStatusRadioEnabled = 1;
    private UIInput newSetSpecInputField;
    private String newSetSpec = "";
    private String newSetDescription = "";
    private String newSetQuery = "";
    
    public String getNewSetSpec() {
        return newSetSpec;
    }
    
    public void setNewSetSpec(String newSetSpec) {
        this.newSetSpec = newSetSpec;
    }
    
    public String getNewSetDescription() {
        return newSetDescription;
    }
    
    public void setNewSetDescription(String newSetDescription) {
        this.newSetDescription = newSetDescription;
    }
    
    public String getNewSetQuery() {
        return newSetQuery;
    }
    
    public void setNewSetQuery(String newSetQuery) {
        this.newSetQuery = newSetQuery;
    }
     
    public int getOaiServerStatusRadio() {
        return this.oaiServerStatusRadio;
    }
    
    public void setOaiServerStatusRadio(int oaiServerStatusRadio) {
        this.oaiServerStatusRadio = oaiServerStatusRadio;
    }
    
    public String init() {
        if (!isSessionUserAuthenticated()) {
            return "/loginpage.xhtml" + navigationWrapper.getRedirectPage();
        } else if (!isSuperUser()) {
            return "/403.xhtml"; 
        }
        
        
        configuredHarvestingSets = oaiSetService.findAll();
        pageMode = PageMode.VIEW;
        
        if (isHarvestingServerEnabled()) {
            oaiServerStatusRadio = oaiServerStatusRadioEnabled;
        } else {
            oaiServerStatusRadio = oaiServerStatusRadioDisabled;
        }
                
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Harvesting Sets", JH.localize("harvestsets.toptip")));
        return null; 
    }
    
    public List<OAISet> getConfiguredOAISets() {
        return configuredHarvestingSets; 
    }
    
    public void setConfiguredOAISets(List<OAISet> oaiSets) {
        configuredHarvestingSets = oaiSets; 
    }
    
    public boolean isHarvestingServerEnabled() {
        return systemConfig.isOAIServerEnabled();
    }
    
    public void toggleHarvestingServerStatus() {
        if (isHarvestingServerEnabled()) {
            systemConfig.disableOAIServer();
        } else {
            systemConfig.enableOAIServer();
        }
    }
    
    public UIInput getNewSetSpecInputField() {
        return newSetSpecInputField;
    }

    public void setNewSetSpecInputField(UIInput newSetSpecInputField) {
        this.newSetSpecInputField = newSetSpecInputField;
    }
    
    public void disableHarvestingServer() {
        systemConfig.disableOAIServer();
    }
    
    public void setSelectedSet(OAISet oaiSet) {
        selectedSet = oaiSet; 
    }
    
    public OAISet getSelectedSet() {
        return selectedSet; 
    }
    
    public void initNewSet(ActionEvent ae) {
        
        this.newSetSpec = "";
        this.newSetDescription = "";
        this.newSetQuery = "";
        
        this.pageMode = PageMode.CREATE;
        this.setSpecValidated = false;
        
    }
    
    public void createSet(ActionEvent ae) {
        
        OAISet newOaiSet = new OAISet();
        
        
        newOaiSet.setSpec(getNewSetSpec());
        newOaiSet.setName(getNewSetSpec());
        newOaiSet.setDescription(getNewSetDescription());
        newOaiSet.setDefinition(getNewSetQuery());
        
        
        try {
            oaiSetService.save(newOaiSet);
            configuredHarvestingSets = oaiSetService.findAll();            
            JsfHelper.addSuccessMessage("Succesfully created OAI set " + newOaiSet.getSpec());

        } catch (Exception ex) {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, "Failed to create OAI set");
             logger.log(Level.SEVERE, "Failed to create OAI set" + ex.getMessage(), ex);
        }
        
        setPageMode(HarvestingSetsPage.PageMode.VIEW);        
    }
    
    // this saves an existing set that the user has edited: 
    
    public void saveSet(ActionEvent ae) {
        
        OAISet oaiSet = getSelectedSet(); 
        
        if (oaiSet == null) {
            // TODO: 
            // tell the user somehow that the set cannot be saved, and advise
            // them to save the settings they have entered. 
        }
        
        
        // will try to save it now:
        
        try {
            oaiSetService.save(oaiSet);
            configuredHarvestingSets = oaiSetService.findAll(); 
                        
            JsfHelper.addSuccessMessage("Succesfully updated OAI set " + oaiSet.getSpec());

        } catch (Exception ex) {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, "Failed to update OAI set.");
             logger.log(Level.SEVERE, "Failed to update OAI set." + ex.getMessage(), ex);
        }
        setPageMode(HarvestingSetsPage.PageMode.VIEW);

        
    }
    
    public boolean isSetSpecValidated() {
        return this.setSpecValidated;
    }
    
    public void setSetSpecValidated(boolean validated) {
        this.setSpecValidated = validated;
    }
    
    public PageMode getPageMode() {
        return this.pageMode;
    } 
    
    public void setPageMode(PageMode pageMode) {
        this.pageMode = pageMode;
    }
    
    public boolean isCreateMode() {
        return PageMode.CREATE == this.pageMode;
    }
    
    public boolean isEditMode() {
        return PageMode.EDIT == this.pageMode;
    }
    
    public boolean isViewMode() {
        return PageMode.VIEW == this.pageMode;
    }
    
    public boolean isSessionUserAuthenticated() {
        
        if (session == null) {
            return false;
        }
        
        if (session.getUser() == null) {
            return false;
        }
        
        if (session.getUser().isAuthenticated()) {
            return true;
        }
        
        return false;
    }
    
    public String getOAISetStats(OAISet oaiSet) {
        List<OAIRecord> records = oaiRecordService.findOaiRecordsBySetName(oaiSet.getSpec());
        
        if (records == null || records.isEmpty()) {
            return "No records (empty set)";
        }
        
        return records.size() + " records exported";
        
    }
    
    public void validateSetSpec() {

        if ( !StringUtils.isEmpty(getNewSetSpec()) ) {

            if (! Pattern.matches("^[a-zA-Z0-9\\_\\-]+$", getNewSetSpec()) ) {
                //input.setValid(false);
                FacesContext.getCurrentInstance().addMessage(getNewSetSpecInputField().getClientId(),
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "", JH.localize("harvestsets.newSetDialog.setspec.invalid")));
                setSetSpecValidated(false);
                return;

                // If it passes the regex test, check 
            } else if ( oaiSetService.findBySpec(getNewSetSpec()) != null ) {
                //input.setValid(false);
                FacesContext.getCurrentInstance().addMessage(getNewSetSpecInputField().getClientId(),
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "", JH.localize("harvestsets.newSetDialog.setspec.alreadyused")));
                setSetSpecValidated(false);
                return;
            }
            setSetSpecValidated(true);
            return;
        } 
        
        // Nickname field is empty:
        FacesContext.getCurrentInstance().addMessage(getNewSetSpecInputField().getClientId(),
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "", JH.localize("harvestsets.newSetDialog.setspec.required")));
        setSetSpecValidated(false);
        return;
    }
    
    public boolean isSuperUser() {
        return session.getUser().isSuperuser();
    }
    }