/*********************************************************************
 * Copyright (c) 2018 Red Hat, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/

context('Check Extensions are installed', () => {
  before(() => {
    cy.visit('http://localhost:3000');

    // maybe it's possible to wait for an element being displayed/hidden
    cy.wait(5000);
  })


  afterEach(() => {
    cy.theiaCleanup();
  });

  // Search that deploying a plugin is there
  it('Command Palette include Plugin', () => {
    cy.theiaCommandPaletteItems('Plugin:').then((value) => {
      expect(value).to.have.length(5);
      expect(value).to.have.members([
        "Hosted Plugin: Restart Instance", "Hosted Plugin: Select Path", "Hosted Plugin: Start Instance", "Hosted Plugin: Stop Instance", "Plugin: Deploy a plugin's id"]);
    })
  });

  // Search that all expected extensions are installed
  it('Expect some extensions are in installed theia', () => {
    cy.theiaExtensionsList().then((value) => {
      expect(value).to.contains.members(['@theia/plugin-ext', '@theia/plugin-ext-vscode', '@theia/java', '@theia/typescript', 'che-theia-ssh-extension', 'theia-machines-extension', '@eclipse-che/theia-factory-extension', '@eclipse-che/che-theia-hosted-plugin-manager-extension', '@eclipse-che/theia-java-extension']);
    });

  })

  // Search that SSH extension is there with actions
  it('Command Palette include SSH extension', () => {
    cy.theiaCommandPaletteItems('SSH:').then((value) => {
      expect(value).to.have.length(4);
      expect(value).to.have.members([
        'SSH: copy public key to clipboard...', 'SSH: create key pair...', 'SSH: delete key pair...', 'SSH: generate key pair...']);
    })
  });

})
