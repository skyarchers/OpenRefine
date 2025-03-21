/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
 * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

function ExtractOperationsDialog(json) {
    var self = this;
    var frame = $(DOM.loadHTML("core", "scripts/dialogs/extract-operations-dialog.html"));
    var elmts = DOM.bind(frame);
  
    elmts.dialogHeader.html($.i18n('core-project/extract-history'));
    elmts.textarea.attr('aria-label',$.i18n('core-project/operation-history-json'))
    elmts.or_proj_extractSave.html($.i18n('core-project/extract-save'));
    elmts.selectAllButton.html($.i18n('core-buttons/select-all'));
    elmts.deselectAllButton.html($.i18n('core-buttons/deselect-all'));
    elmts.saveJsonAsFileButton.html($.i18n('core-buttons/export'))
    elmts.closeButton.html($.i18n('core-buttons/close'));
  
    var entryTable = elmts.entryTable[0];
    var createEntry = function(entry) {
      var tr = entryTable.insertRow(entryTable.rows.length);
      var td0 = tr.insertCell(0);
      var td1 = tr.insertCell(1);
      td0.width = "1%";
  
      if ("operation" in entry) {
        entry.selected = true;
  
        $('<input type="checkbox" checked="true" />').appendTo(td0).on('click',function() {
          entry.selected = !entry.selected;
          updateJson();
        });
  
        $('<span>').text(entry.operation.description).appendTo(td1);
      } else {
        $('<span>').text(entry.description).css("color", "#888").appendTo(td1);
      }
    };
    for (var i = 0; i < json.entries.length; i++) {
      createEntry(json.entries[i]);
    }
  
    var updateJson = function() {
      var a = [];
      for (var i = 0; i < json.entries.length; i++) {
        var entry = json.entries[i];
        if ("operation" in entry && entry.selected) {
          a.push(entry.operation);
        }
      }
      elmts.textarea.text(JSON.stringify(a, null, 2));
    };
    updateJson();
  
    elmts.closeButton.on('click',function() { DialogSystem.dismissUntil(level - 1); });
    elmts.selectAllButton.on('click',function() {
      for (var i = 0; i < json.entries.length; i++) {
        json.entries[i].selected = true;
      }
  
      frame.find('input[type="checkbox"]').prop('checked', true);
      updateJson();
    });
    elmts.deselectAllButton.on('click',function() {
      for (var i = 0; i < json.entries.length; i++) {
        json.entries[i].selected = false;
      }
  
      frame.find('input[type="checkbox"]').prop('checked', false);
      updateJson();
    });
    elmts.saveJsonAsFileButton.on('click',function() {
      var historyJson = elmts.textarea[0].value;
  
      downloadFile('history.json', historyJson);
    });
  
    // Function originally created by Matěj Pokorný at StackOverflow:
    // https://stackoverflow.com/a/18197341/5564816
    var downloadFile = function(filename, content) {
      var element = document.createElement('a');
      element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(content));
      element.setAttribute('download', filename);
  
      element.style.display = 'none';
      document.body.appendChild(element);
  
      element.click();
      document.body.removeChild(element);
    }
  
    var level = DialogSystem.showDialog(frame);
  
    elmts.textarea[0].select();
}