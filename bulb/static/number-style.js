(function () {
  'use strict';

  /** enhance a number input and add styled DOM buttons */
  function enhanceNumber(num) {
    const downButton = document.createElement('button');
    downButton.textContent = '-';
    downButton.classList.add('number-down');
    downButton.addEventListener('click', () => {
      downButton.parentNode.querySelector('input[type="number"]').stepDown();
    });
    
    const upButton = document.createElement('button');
    upButton.textContent = '+';
    upButton.classList.add('number-up');
    upButton.addEventListener('click', () => {
      upButton.parentNode.querySelector('input[type="number"]').stepUp();
    });

    // Put the down button, number input, and up button in a single div
    const parentDiv = document.createElement('div');
    parentDiv.classList.add('number-div');

    num.parentNode.insertBefore(parentDiv, num);

    // Insert the buttons
    parentDiv.appendChild(downButton);
    parentDiv.appendChild(num);
    parentDiv.appendChild(upButton);
  }

  /** Add the buttons to every number input in list */
  function enhanceNumbers(nums) {
    nums.forEach((num) => {
      // If it's in a number-div, it's already enhanced
      if(!num.parentNode.classList.contains('number-div'))
        enhanceNumber(num);
    });
  }

  /** Select all number inputs and enhance */
  function autoEnhance() {
    const nums = document.querySelectorAll('input[type="number"]');
    if (nums) {
      enhanceNumbers(nums);
      return true;
    }
    return false;
  }

  // Add to namespace NumberStyle to test in dev console
  window.NumberStyle = window.NumberStyle || {};
  window.NumberStyle.enhanceNumbers = enhanceNumbers;
  window.NumberStyle.enhanceNumber = enhanceNumber;
  window.NumberStyle.autoEnhance = autoEnhance;

  // Observer to dynamically enhance new inputs as they appear
  const observer = new MutationObserver((mutationsList, observer) => {
    for (const mutation of mutationsList) {
      if (mutation.type === 'childList') {
        //console.log('A child node has been added or removed.');
        // Call your function here
        //enhanceNumbers(document.querySelectorAll('input[type="number"]')); 
        autoEnhance();
      }
    }
  });

  observer.observe(document.getElementById("sidebar"), {
    childList: true,
    subtree: true,
    attributes: false,
  });
})();
